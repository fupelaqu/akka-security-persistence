package org.softnetwork.security.actors

import java.util.Date

import akka.actor.ActorLogging
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import org.softnetwork.akka.message._
import org.softnetwork.notification.handlers.NotificationHandler
import org.softnetwork.security.message._
import org.softnetwork.security.model.{AccountStatus, VerificationToken, Account, Password}
import Password._

import scala.util.{Success, Try}

/**
  * Created by smanciot on 31/03/2018.
  */
trait AccountStateActor[T <: Account] extends PersistentActor with ActorLogging {

  def notificationHandler(): NotificationHandler

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

  def sendConfirmation(account: T): Try[Boolean] = Success(false) //TODO Notifications

  def updateState(event: Event): Unit = {

    event match {

      case evt: AccountCreated[T] =>
        val account = evt.account
        import account._
        state = state.copy(accounts = state.accounts.updated(uuid, account))
        secondaryPrincipals.foreach((principal) =>
          state = state.copy(principals = state.principals.updated(principal.value, uuid))
        )
        val verificationToken = VerificationToken(account.primaryPrincipal.value)
        state = state.copy(
          verificationTokens = state.verificationTokens.updated(
            verificationToken.token,
            VerificationTokenWithUuid(
              verificationToken,
              uuid,
              sendConfirmation(account).getOrElse(false)
            )
          )
        )

      /** secondary principals should not be updated if they have been defined **/
      case evt: AccountUpdated[T] =>
        val account = evt.account
        import account._
        state = state.copy(accounts = state.accounts.updated(uuid, account))

      case _                      =>
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
              persist(
                AccountCreated(
                  account
                    .copyWithStatus(AccountStatus.Inactive)
                    .addAll(secondaryPrincipals).asInstanceOf[T]
                )
              ) { event =>
                updateState(event)
                context.system.eventStream.publish(event)
                sender() ! event
                performSnapshotIfRequired()
              }
            }
            else
              sender() ! LoginAlreadyExists
          case _             => sender() ! LoginUnaccepted
        }
      }

    /** handle Confirm **/
    case cmd: Confirm =>
      state.verificationTokens.get(cmd.token) match {
        case Some(s) =>
          if(s.check) {

          }
          else
            sender() ! TokenExpired
        case _       => sender() ! TokenNotFound
      }

    /** handle login **/
    case cmd: Login   =>
      import cmd._
      lookupAccount(login) match {
        case Some(account) =>
          if(checkPassword(account.credentials, cmd.password)){
            // TODO check account state
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
        case _       => sender() ! LoginAndPasswordNotMatched //WrongLogin
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
            if(checkPassword(credentials, oldPassword)){
              persist(
                new PasswordUpdated(
                  account
                    .copyWithCredentials(sha512(newPassword))
                    .addAll(account.secondaryPrincipals)
                    .asInstanceOf[T]                )
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
   verificationTokens: Map[String, VerificationTokenWithUuid] = Map.empty
)

case class VerificationTokenWithUuid(verificationToken: VerificationToken, uuid: String, notified: Boolean = false){
  def check: Boolean = verificationToken.expirationDate >= new Date().getTime
}
