package org.softnetwork.security.actors

import java.util.Date

import akka.actor.ActorLogging
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import mustache.Mustache
import org.softnetwork.akka.message._
import org.softnetwork.notification.handlers.NotificationHandler
import org.softnetwork.notification.message.{MailSent, SendMail}
import org.softnetwork.notification.model.Mail
import org.softnetwork.security.config.Settings._
import org.softnetwork.security.handlers.Generator
import org.softnetwork.security.message._
import org.softnetwork.security.model._
import Sha512Encryption._
import Hash._

import scala.io.Source

/**
  * Created by smanciot on 31/03/2018.
  */
trait AccountStateActor[T <: Account] extends PersistentActor with ActorLogging {

  def notificationHandler(): NotificationHandler

  def generator(): Generator

  var state: AccountState[T]

  /** number of events received before generating a snapshot - should be configurable **/
  def snapshotInterval: Long

  /** number of login failures authorized before disabling user account - should be configurable **/
  def maxFailures: Int

  private def lookupAccount(key: String): Option[T] = {
    state.accounts.get(key) match {
      case x: Some[T] => x
      case _          => state.principals.get(key) match {
          case Some(uuid) => state.accounts.get(uuid)
          case _          => None
        }
    }
  }

  def createAccount(cmd: SignIn): Option[T]

  def sendActivation(account: T, activationToken: VerificationToken): Boolean = {
    account.email match {
      case Some(email) =>
        val activationUrl = s"$BaseUrl/$Path/activate/${activationToken.token}"
        val template = new Mustache(
          Source.fromFile(
            Thread.currentThread().getContextClassLoader.getResource("notification/activation.mustache").getPath
          )
        )
        val body = template.render(Map("account" -> account, "activationUrl" -> activationUrl))
        notificationHandler().handle(
          SendMail(
            Mail(
              (MailFrom, "nobody"),
              Seq(email),
              Seq(),
              Seq(),
              "Activation",
              body,
              Some(body)
            )
          )
        ) match {
          case _: MailSent.type => true
          case _                => false
        }
      case _          => false // TODO push notification
    }
  }

  def sendVerificationCode(account: T, verificationCode: VerificationCode): Boolean = {
    account.email match {
      case Some(email) =>
        val template = new Mustache(
          Source.fromFile(
            Thread.currentThread().getContextClassLoader.getResource("notification/reset_password.mustache").getPath
          )
        )
        val body = template.render(Map("account" -> account, "code" -> verificationCode.code))
        notificationHandler().handle(
          SendMail(
            Mail(
              (MailFrom, "nobody"),
              Seq(email),
              Seq(),
              Seq(),
              "Reset Password",
              body,
              Some(body)
            )
          )
        ) match {
          case _: MailSent.type => true
          case _                => false
        }
      case _           => false // TODO push notification
    }
  }

  def updateState(event: Event): Unit = {

    event match {

      case evt: AccountCreated[T]     =>
        val account = evt.account
        import account._
        state = state.copy(accounts = state.accounts + (uuid->account))
        secondaryPrincipals.foreach((principal) =>
          state = state.copy(principals = state.principals.updated(principal.value, uuid))
        )

      /** secondary principals should not be updated if they have been defined **/
      case evt: AccountUpdated[T]     =>
        val account = evt.account
        import account._
        state = state.copy(accounts = state.accounts.updated(uuid, account))

      case evt: ActivationTokenEvent =>
        import evt._
        state.accounts.get(activationToken.uuid) match {
          case Some(account) =>
            val updatedAccount = account.copyWithActivationToken(Some(activationToken.verificationToken)).addAll(account.principals).asInstanceOf[T]
            state = state.copy(
              accounts = state.accounts.updated(
                account.uuid,
                updatedAccount
              )
            )
          case _             =>
        }
        state = state.copy(
          activationTokens = state.activationTokens.updated(
            md5Hash(activationToken.verificationToken.token),
            activationToken
          )
        )

      case evt: RemoveActivationTokenEvent =>
        import evt._
        state = state.copy(
          activationTokens = state.activationTokens - md5Hash(activationToken)
        )

      case evt: VerificationCodeEvent =>
        import evt._
        state.accounts.get(verificationCode.uuid) match {
          case Some(account) =>
            val updatedAccount = account.copyWithVerificationCode(Some(verificationCode.verificationCode)).addAll(account.principals).asInstanceOf[T]
            state = state.copy(
              accounts = state.accounts.updated(
                updatedAccount.uuid,
                updatedAccount
              )
            )
          case _             =>
        }
        state = state.copy(
          verificationCodes = state.verificationCodes.updated(
            md5Hash(verificationCode.verificationCode.code),
            verificationCode
          )
        )

      case evt: RemoveVerificationCodeEvent =>
        import evt._
        state = state.copy(
          verificationCodes = state.verificationCodes - md5Hash(verificationCode)
        )

      case _                          =>
    }
  }

  override val receiveRecover: Receive = {
    case e: Event                                    => updateState(e)
    case SnapshotOffer(_, snapshot: AccountState[T]) => state = snapshot
    case RecoveryCompleted                           => log.info(s"AccountState has been recovered")
  }

  override val receiveCommand: Receive = {

    /** handle signIn **/
    case cmd: SignIn =>
      import cmd._
      if(!password.equals(confirmPassword)){
        sender() ! PasswordsNotMatched
      }
      else{
        createAccount(cmd) match {
          case Some(account) =>
            import account._
            val exists = secondaryPrincipals.find(
              (principal) => state.principals.contains(principal.value)
            ) match {
              case Some(_) => true
              case _       => false
            }
            if(!exists){
              persist(AccountCreated(account)) { event =>
                updateState(event)
                context.system.eventStream.publish(event)

                val activationRequired = status == AccountStatus.Inactive
                var notified = false
                if(activationRequired) { // an activation is required
                  val activationToken = generator().generateToken(
                    account.primaryPrincipal.value,
                    ActivationTokenExpirationTime
                  )
                  notified = sendActivation(account, activationToken)
                  persist(
                    ActivationTokenEvent(
                      ActivationTokenWithUuid(
                        activationToken,
                        uuid,
                        notified
                      )
                    )
                  ) { event2 =>
                    updateState(event2)
                    context.system.eventStream.publish(event2)
                  }
                }

                sender() ! {if(activationRequired && ! notified) UndeliveredActivationToken else event}

                performSnapshotIfRequired()
              }
            }
            else
              sender() ! LoginAlreadyExists
          case _             => sender() ! LoginUnaccepted
        }
      }

    /** handle account activation **/
    case cmd: Activate =>
      import cmd._
      state.activationTokens.get(md5Hash(token)) match {
        case Some(activation) =>
          if(activation.check) {
            state.accounts.get(activation.uuid) match {
              case Some(account) if account.status == AccountStatus.Inactive =>
                persist(
                  new AccountActivated(
                    account.copyWithStatus(AccountStatus.Active)
                    .addAll(account.secondaryPrincipals)
                    .asInstanceOf[T]
                  )
                ){event =>
                  updateState(event)
                  context.system.eventStream.publish(event)
                  persistAsync(RemoveActivationTokenEvent(token)){event2 =>
                    updateState(event2)
                    context.system.eventStream.publish(event2)
                  }
                  sender() ! event
                  performSnapshotIfRequired()
                }
              case None                                                      => sender() ! AccountNotFound
              case _                                                         => sender() ! IllegalStateError
            }
          }
          else
            sender() ! TokenExpired
        case _       => sender() ! TokenNotFound
      }

    /** handle login **/
    case cmd: Login   =>
      import cmd._
      lookupAccount(login) match {
        case Some(account) if account.status == AccountStatus.Active =>
          if(checkEncryption(account.credentials, cmd.password)){
            persist(
              new LoginSucceeded(
                account
                  .copyWithNbLoginFailures(0)// reset number of login failures
                  .copyWithLastLogin(Some(new Date()))
                  .addAll(account.secondaryPrincipals)
                  .asInstanceOf[T]              )
            ) {event =>
              updateState(event)
              context.system.eventStream.publish(event)
              sender() ! event
              performSnapshotIfRequired()
            }
          }
          else { // wrong password
            val nbLoginFailures = account.nbLoginFailures + 1
            val disabled = nbLoginFailures > maxFailures // disable account
            val status = if(disabled) AccountStatus.Disabled else account.status
            persist(
              new LoginFailed(
                account
                  .copyWithNbLoginFailures(nbLoginFailures)
                  .copyWithStatus(status)
                  .addAll(account.secondaryPrincipals)
                  .asInstanceOf[T]
              )
            ){ event =>
              updateState(event)
              context.system.eventStream.publish(event)
              if(disabled){
                sender() ! AccountDisabled
              }
              else{
                sender() ! LoginAndPasswordNotMatched
              }
              performSnapshotIfRequired()
            }
          }
        case None                                                 => sender() ! LoginAndPasswordNotMatched //WrongLogin
        case _                                                    => sender() ! IllegalStateError
      }

    /** handle send verification code **/
    case cmd: SendVerificationCode =>
      import cmd._
      if(EmailValidator.check(principal) || GsmValidator.check(principal)){
        lookupAccount(principal) match {
          case Some(account) =>
            account.verificationCode match {
              case Some(verification) =>
                persist(
                  RemoveVerificationCodeEvent(
                    verification.code
                  )
                ){event =>
                  updateState(event)
                  context.system.eventStream.publish(event)
                }
              case _                =>
            }
            val verificationCode = generator().generatePinCode(VerificationCodeSize, VerificationCodeExpirationTime)
            persist(
              VerificationCodeEvent(
                VerificationCodeWithUuid(
                  verificationCode,
                  account.uuid,
                  sendVerificationCode(account, verificationCode)
                )
              )
            ) {event =>
              updateState(event)
              context.system.eventStream.publish(event)
              sender() ! {if(event.verificationCode.notified) VerificationCodeSent else UndeliveredVerificationCode}
            }
          case _             => sender() ! AccountNotFound
        }
      }
      else{
        sender() ! InvalidPrincipal
      }

    case cmd: ResetPassword =>
      import cmd._
      if(!newPassword.equals(confirmedPassword)){
        sender() ! PasswordsNotMatched
      }
      else{
        state.verificationCodes.get(md5Hash(code)) match {
          case Some(verification) =>
            if(verification.check){
              state.accounts.get(verification.uuid) match {
                case Some(account) =>
                  persist(
                    new PasswordUpdated(
                      account
                        .copyWithCredentials(encrypt(newPassword))
                        .copyWithStatus(AccountStatus.Active) //TODO check this
                        .copyWithNbLoginFailures(0)
                        .addAll(account.secondaryPrincipals)
                        .asInstanceOf[T]
                    )
                  ) {event =>
                    updateState(event)
                    context.system.eventStream.publish(event)
                    persistAsync(RemoveVerificationCodeEvent(code)){event2 =>
                      updateState(event2)
                      context.system.eventStream.publish(event2)
                    }
                    sender() ! PasswordReseted
                    performSnapshotIfRequired()
                  }
                case _             => sender() ! AccountNotFound
              }
            }
            else {
              sender() ! CodeExpired
            }
          case _                  => sender() ! CodeNotFound
        }
      }

    /** handle update password **/
    case cmd: UpdatePassword =>
      import cmd._
      if(!newPassword.equals(confirmedPassword)){
        sender() ! PasswordsNotMatched
      }
      else{
        lookupAccount(login) match {
          case Some(account) =>
            import account._
            if(checkEncryption(credentials, oldPassword)){
              persist(
                new PasswordUpdated(
                  account
                    .copyWithCredentials(encrypt(newPassword))
                    .copyWithStatus(AccountStatus.Active)
                    .copyWithNbLoginFailures(0)
                    .addAll(account.secondaryPrincipals)
                    .asInstanceOf[T]
                )
              ) {event =>
                updateState(event)
                context.system.eventStream.publish(event)
                sender() ! event
                performSnapshotIfRequired()
              }
            }
            else {
              sender() ! LoginAndPasswordNotMatched
            }
          case _       => sender() ! LoginUnaccepted
        }
      }

    /** handle signOut **/
    case cmd: SignOut        =>
      import cmd._
      lookupAccount(uuid) match {
        case Some(account) =>
          val updatedAccount = account
            .copyWithStatus(AccountStatus.Deleted)
            .addAll(account.secondaryPrincipals)
            .asInstanceOf[T]
          persist(new AccountDeleted(updatedAccount)) {event =>
            updateState(event)
            context.system.eventStream.publish(event)
            sender() ! event
            performSnapshotIfRequired()
          }
        case _             => sender() ! AccountNotFound
      }

    case cmd: Logout.type    => sender() ! LogoutSucceeded

    /** no handlers **/
    case _                   => sender() ! UnknownCommand
  }

  private def performSnapshotIfRequired(): Unit = {
    if (lastSequenceNr % snapshotInterval == 0 && lastSequenceNr != 0)
      saveSnapshot(state)
  }
}

case class AccountState[T <: Account](
   accounts: Map[String, T] = Map[String, T](),
   principals: Map[String, String] = Map.empty,
   activationTokens: Map[String, ActivationTokenWithUuid] = Map.empty,
   verificationCodes: Map[String, VerificationCodeWithUuid] = Map.empty
)

case class ActivationTokenWithUuid(verificationToken: VerificationToken, uuid: String, notified: Boolean = false){
  def check: Boolean = verificationToken.expirationDate >= new Date().getTime
}

case class VerificationCodeWithUuid(verificationCode: VerificationCode, uuid: String, notified: Boolean = false){
  def check: Boolean = verificationCode.expirationDate >= new Date().getTime
}
