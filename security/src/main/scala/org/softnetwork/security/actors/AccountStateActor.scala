package org.softnetwork.security.actors

import java.util.Date

import akka.actor.ActorLogging
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import mustache.Mustache
import org.softnetwork.akka.message._
import org.softnetwork.notification.handlers.{PushHandler, SMSHandler, MailHandler}
import org.softnetwork.notification.message._
import org.softnetwork.notification.model.NotificationType.NotificationType
import org.softnetwork.notification.model._
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

  def mailHandler(): MailHandler

  def smsHandler(): SMSHandler

  def pushHandler(): PushHandler

  def generator(): Generator

  var state: AccountState[T]

  val rules = passwordRules()

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

  private def sendMail(account: T,
                       subject: String,
                       body: String,
                       maxTries: Int = 1,
                       deferred: Option[Date] = None): Boolean = {
    account.email match {
      case Some(email) =>
        mailHandler().handle(
          SendNotification(
            Mail(
              (MailFrom, Some("nobody")),
              Seq(email),
              Seq(),
              Seq(),
              subject,
              body,
              Some(body),
              maxTries = maxTries,
              deferred = deferred
            )
          )
        ) match {
          case _: NotificationSent      => true
          case _: NotificationDelivered => true
          case _                        => false
        }
      case _ => false
    }
  }

  private def sendSMS(account: T,
                      subject: String,
                      body: String,
                      maxTries: Int = 1,
                      deferred: Option[Date] = None): Boolean = {
    account.gsm match {
      case Some(gsm) =>
        smsHandler().handle(
          SendNotification(
            SMS(
              (SMSClientId, Some("nobody")),
              Seq(gsm),
              subject,
              body,
              maxTries = maxTries,
              deferred = deferred
            )
          )
        ) match {
          case _: NotificationSent      => true
          case _: NotificationDelivered => true
          case _                        => false
        }
      case _ => false
    }
  }

  private def sendPush(account: T,
                        subject: String,
                        body: String,
                        maxTries: Int = 1,
                        deferred: Option[Date] = None): Boolean = {
    state.accountRegistrations.get(account.uuid) match {
      case Some(registrations) =>
        val devices = registrations.flatMap(
          (regId) => state.deviceRegistrations.get(regId).map(
            (registration) => BasicDevice(registration.regId, registration.platform)
          )
        ).toSeq
        pushHandler().handle(
          SendNotification(
            Push(
              from = (PushClientId, None),
              subject = subject,
              message = body,
              devices = devices,
              id = "", //TODO
              maxTries = maxTries,
              deferred = deferred
            )
          )
        ) match {
          case _: NotificationSent      => true
          case _: NotificationDelivered => true
          case _                        => false
        }
      case _                 => false
    }
  }

  private def sendNotificationByChannel(account: T,
                              subject: String,
                              body: String,
                              channel: NotificationType,
                              maxTries: Int = 1,
                              deferred: Option[Date] = None): Boolean = {
    channel match {
      case NotificationType.Mail => sendMail(account, subject, body)
      case NotificationType.SMS  => sendSMS(account, subject, body)
      case NotificationType.Push => sendPush(account, subject, body)
      case _                     => false
    }
  }

  def sendNotification(account: T,
                        subject: String,
                        body: String,
                        channels: Seq[NotificationType],
                        maxTries: Int = 1,
                        deferred: Option[Date] = None): Boolean = {
    channels.exists((channel) => sendNotificationByChannel(account, subject, body, channel, maxTries, deferred))
  }

  def sendActivation(account: T,
                     activationToken: VerificationToken,
                     maxTries: Int = 1,
                     deferred: Option[Date] = None): Boolean = {
    val subject = "Activation"

    val body = new Mustache(
      Source.fromFile(
        Thread.currentThread().getContextClassLoader.getResource("notification/activation.mustache").getPath
      )
    ).render(Map("account" -> account, "activationUrl" -> s"$BaseUrl/$Path/activate/${activationToken.token}"))

    sendNotification(
      account,
      subject,
      body,
      Seq(NotificationType.Mail, NotificationType.Push, NotificationType.SMS),
      maxTries,
      deferred
    )
  }

  def sendVerificationCode(account: T,
                           verificationCode: VerificationCode,
                           maxTries: Int = 1,
                           deferred: Option[Date] = None): Boolean = {
    val subject = "Reset Password"

    val body = new Mustache(
      Source.fromFile(
        Thread.currentThread().getContextClassLoader.getResource("notification/reset_password.mustache").getPath
      )
    ).render(Map("account" -> account, "code" -> verificationCode.code))

    sendNotification(
      account,
      subject,
      body,
      Seq(NotificationType.Push, NotificationType.Mail, NotificationType.SMS),
      maxTries,
      deferred
    )
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

      case evt: RegisterDeviceEvent   =>
        import evt.registration._
        val regId = registration.regId
        state = state.copy(
          deviceRegistrations = state.deviceRegistrations.updated(regId, registration),
          accountRegistrations = state.accountRegistrations.updated(
            uuid,
            state.accountRegistrations.getOrElse(uuid, Set.empty) + regId
          )
        )

      case evt: UnregisterDeviceEvent =>
        import evt.registration._
        val regId = registration.regId
        state = state.copy(
          deviceRegistrations = state.deviceRegistrations - regId,
          accountRegistrations = state.accountRegistrations.updated(
            uuid,
            state.accountRegistrations.getOrElse(uuid, Set.empty) - regId
          )
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
        rules.validate(password) match {
          case Left(errorCodes)          => sender() ! InvalidPassword(errorCodes)
          case Right(success) if success =>
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
        rules.validate(newPassword) match {
          case Left(errorCodes)          => sender() ! InvalidPassword(errorCodes)
          case Right(success) if success =>
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
      }

    /** handle update password **/
    case cmd: UpdatePassword =>
      import cmd._
      if(!newPassword.equals(confirmedPassword)){
        sender() ! PasswordsNotMatched
      }
      else{
        rules.validate(newPassword) match {
          case Left(errorCodes)          => sender() ! InvalidPassword(errorCodes)
          case Right(success) if success =>
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
      }

    /**
      * handle device registration
      */
    case cmd: RegisterDevice =>
      import cmd._
      lookupAccount(uuid) match {
        case Some(account) =>
          persist(new RegisterDeviceEvent(DeviceRegistrationWithUuid(registration, uuid))) {event =>
            updateState(event)
            context.system.eventStream.publish(event)
            sender() ! DeviceRegistered
            performSnapshotIfRequired()
          }
        case _             => sender() ! AccountNotFound
      }

    /**
      * handle device unregistration
      */
    case cmd: UnregisterDevice =>
      import cmd._
      lookupAccount(uuid) match {
        case Some(account) =>
          state.deviceRegistrations.get(regId) match {
            case Some(registration) =>
              persist(new UnregisterDeviceEvent(DeviceRegistrationWithUuid(registration, uuid))) {event =>
                updateState(event)
                context.system.eventStream.publish(event)
                sender() ! DeviceUnregistered
                performSnapshotIfRequired()
              }
            case _                  => sender() ! DeviceRegistrationNotFound
          }
        case _             => sender() ! AccountNotFound
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
   verificationCodes: Map[String, VerificationCodeWithUuid] = Map.empty,
   deviceRegistrations: Map[String, DeviceRegistration] = Map.empty,
   accountRegistrations: Map[String, Set[String]] = Map.empty
)

case class ActivationTokenWithUuid(verificationToken: VerificationToken, uuid: String, notified: Boolean = false){
  def check: Boolean = verificationToken.expirationDate >= new Date().getTime
}

case class VerificationCodeWithUuid(verificationCode: VerificationCode, uuid: String, notified: Boolean = false){
  def check: Boolean = verificationCode.expirationDate >= new Date().getTime
}

case class DeviceRegistrationWithUuid(registration: DeviceRegistration, uuid: String)
