package org.softnetwork.security.persistence.typed

import java.util.Date

import _root_.akka.actor.typed.scaladsl.{ActorContext, TimerScheduler}

import akka.actor.typed.{ActorRef, ActorSystem}

import akka.persistence.typed.scaladsl.Effect

import mustache.Mustache
import org.slf4j.Logger
import org.softnetwork.Sha512Encryption

import org.softnetwork.akka.model.State

import org.softnetwork.akka.persistence.typed.EntityBehavior
import org.softnetwork.notification.handlers.{MockNotificationDao, NotificationDao}

import org.softnetwork.notification.model._
import org.softnetwork.notification.model.NotificationType

import org.softnetwork.notification.peristence.typed._

import org.softnetwork.security.config.Settings._

import org.softnetwork.security.handlers._

import org.softnetwork.security.message._
import Sha512Encryption._

import org.softnetwork.security.model._

import org.softnetwork._

import scala.language.{postfixOps, implicitConversions}
import scala.reflect.ClassTag

/**
  * Created by smanciot on 17/04/2020.
  */
object Accounts {

  @SerialVersionUID(0L)
  case class AccountKeyState(key: String, account: String) extends State {
    val uuid = key
  }

}

trait AccountNotifications[T <: Account] {

  def notificationDao: NotificationDao = NotificationDao

  /** number of login failures authorized before disabling user account **/
  val maxLoginFailures: Int = MaxLoginFailures

  protected def activationTokenUuid(entityId: String): String = {
    s"$entityId-activation-token"
  }

  protected def registrationUuid(entityId: String): String = {
    s"$entityId-registration"
  }

  private[this] def sendMail(
                              uuid: String,
                              account: T,
                              subject: String,
                              body: String,
                              maxTries: Int,
                              deferred: Option[Date])(implicit system: ActorSystem[_]): Boolean = {
    account.email match {
      case Some(email) =>
        notificationDao.sendNotification(
          Mail.defaultInstance
            .withUuid(uuid)
            .withFrom(From(MailFrom, Some(MailName)))
            .withTo(Seq(email))
            .withSubject(subject)
            .withMessage(body)
            .withRichMessage(body)
            .withMaxTries(maxTries)
            .withDeferred(deferred.orNull)
        )
      case _ => false
    }
  }

  private[this] def sendSMS(
                             uuid: String,
                             account: T,
                             subject: String,
                             body: String,
                             maxTries: Int,
                             deferred: Option[Date])(implicit system: ActorSystem[_]): Boolean = {
    account.gsm match {
      case Some(gsm) =>
        notificationDao.sendNotification(
          SMS.defaultInstance
            .withUuid(uuid)
            .withFrom(From(SMSClientId, Some(SMSName)))
            .withTo(Seq(gsm))
            .withSubject(subject)
            .withMessage(body)
            .withMaxTries(maxTries)
            .withDeferred(deferred.orNull)
        )
      case _ => false
    }
  }

  private[this] def sendPush(
                              uuid: String,
                              account: T,
                              subject: String,
                              body: String,
                              maxTries: Int,
                              deferred: Option[Date],
                              registrations: Seq[DeviceRegistration])(implicit system: ActorSystem[_]): Boolean = {
    registrations.isEmpty ||
      notificationDao.sendNotification(
        Push.defaultInstance
          .withUuid(uuid)
          .withFrom(From.defaultInstance.withValue(PushClientId))
          .withSubject(subject)
          .withMessage(body)
          .withDevices(registrations.map((registration) => BasicDevice(registration.regId, registration.platform)))
          .withMaxTries(maxTries)
          .withDeferred(deferred.orNull)
      )
  }

  private[this] def sendNotificationByChannel(
                                               uuid: String,
                                               account: T,
                                               subject: String,
                                               body: String,
                                               channel: NotificationType,
                                               maxTries: Int,
                                               deferred: Option[Date],
                                               registrations: Seq[DeviceRegistration] = Seq.empty)(
    implicit system: ActorSystem[_]): Boolean = {
    channel match {
      case NotificationType.MAIL_TYPE => sendMail(uuid, account, subject, body, maxTries, deferred)
      case NotificationType.SMS_TYPE  => sendSMS(uuid, account, subject, body, maxTries, deferred)
      case NotificationType.PUSH_TYPE => sendPush(uuid, account, subject, body, maxTries, deferred, registrations)
      case _ => false
    }
  }

  private[this] def sendNotification(
                                      uuid: String,
                                      account: T,
                                      subject: String,
                                      body: String,
                                      channels: Seq[NotificationType],
                                      maxTries: Int = 1,
                                      deferred: Option[Date] = None)(
    implicit log: Logger, system: ActorSystem[_]): Boolean = {
    log.info(s"about to send notification to ${account.primaryPrincipal.value}\r\n$body")
    channels.exists((channel) => sendNotificationByChannel(uuid, account, subject, body, channel, maxTries, deferred))
  }

  def sendActivation(
                      uuid: String,
                      account: T,
                      activationToken: VerificationToken,
                      maxTries: Int = 3,
                      deferred: Option[Date] = None)(implicit log: Logger, system: ActorSystem[_]): Boolean = {
    val subject = NotificationsConfig.activation

    val body = Mustache("notification/activation.mustache").render(
      Map(
        "firstName" -> (account.details match {
          case Some(s) => s.firstName
          case _       => "customer"
        }),
        "activationUrl" -> s"$BaseUrl/$Path/activate/${activationToken.token}"
      )
    )

    sendNotification(
      activationTokenUuid(uuid),
      account,
      subject,
      body,
      Seq(NotificationType.MAIL_TYPE, NotificationType.PUSH_TYPE, NotificationType.SMS_TYPE),
      maxTries,
      deferred
    )
  }

  def sendRegistration(
                        uuid: String,
                        account: T,
                        maxTries: Int = 2,
                        deferred: Option[Date] = None)(implicit log: Logger, system: ActorSystem[_]): Boolean = {
    val subject = NotificationsConfig.registration

    val body = Mustache("notification/registration.mustache").render(
      Map(
        "firstName" -> (account.details match {
          case Some(s) => s.firstName
          case _       => "customer"
        })
      )
    )

    sendNotification(
      registrationUuid(uuid),
      account,
      subject,
      body,
      Seq(NotificationType.MAIL_TYPE, NotificationType.PUSH_TYPE, NotificationType.SMS_TYPE),
      maxTries,
      deferred
    )
  }

  def sendVerificationCode(
                            uuid: String,
                            account: T,
                            verificationCode: VerificationCode,
                            maxTries: Int = 1,
                            deferred: Option[Date] = None)(implicit log: Logger, system: ActorSystem[_]): Boolean = {
    val subject = NotificationsConfig.resetPassword

    val body = Mustache("notification/verification_code.mustache").render(
      Map(
        "firstName" -> (account.details match {
          case Some(s) => s.firstName
          case _       => "customer"
        }),
        "code" -> verificationCode.code
      )
    )

    sendNotification(
      uuid,
      account,
      subject,
      body,
      Seq(NotificationType.PUSH_TYPE, NotificationType.MAIL_TYPE, NotificationType.SMS_TYPE),
      maxTries,
      deferred
    )
  }

  def sendAccountDisabled(
                           uuid: String,
                           account: T,
                           maxTries: Int = 1,
                           deferred: Option[Date] = None)(implicit log: Logger, system: ActorSystem[_]): Boolean = {
    val subject = NotificationsConfig.accountDisabled

    val body = Mustache("notification/account_disabled.mustache").render(
      Map(
        "firstName" -> (account.details match {
          case Some(s) => s.firstName
          case _       => "customer"
        }),
        "resetPasswordUrl" -> ResetPasswordUrl,
        "loginFailures" -> (maxLoginFailures + 1)
      )
    )

    sendNotification(
      uuid,
      account,
      subject,
      body,
      Seq(NotificationType.MAIL_TYPE, NotificationType.PUSH_TYPE, NotificationType.SMS_TYPE),
      maxTries,
      deferred
    )
  }

  def sendResetPassword(
                         uuid: String,
                         account: T,
                         verificationToken: VerificationToken,
                         maxTries: Int = 1,
                         deferred: Option[Date] = None)(implicit log: Logger, system: ActorSystem[_]): Boolean = {
    val subject = NotificationsConfig.resetPassword

    val body = Mustache("notification/reset_password.mustache").render(
      Map(
        "firstName" -> (account.details match {
          case Some(s) => s.firstName
          case _       => "customer"
        }),
        "resetPasswordUrl" -> s"$ResetPasswordUrl?token=${verificationToken.token}"
      )
    )

    sendNotification(
      uuid,
      account,
      subject,
      body,
      Seq(NotificationType.MAIL_TYPE, NotificationType.PUSH_TYPE, NotificationType.SMS_TYPE),
      maxTries,
      deferred
    )
  }

  def sendPasswordUpdated(
                           uuid: String,
                           account: T,
                           maxTries: Int = 1,
                           deferred: Option[Date] = None)(implicit log: Logger, system: ActorSystem[_]): Boolean = {
    val subject = NotificationsConfig.passwordUpdated

    val body = Mustache("notification/password_updated.mustache").render(
      Map(
        "firstName" -> (account.details match {
          case Some(s) => s.firstName
          case _       => "customer"
        })
      )
    )

    sendNotification(
      uuid,
      account,
      subject,
      body,
      Seq(NotificationType.MAIL_TYPE, NotificationType.PUSH_TYPE, NotificationType.SMS_TYPE),
      maxTries,
      deferred
    )
  }

  def removeActivation(uuid: String)(implicit system: ActorSystem[_]) = _removeNotification(
    activationTokenUuid(uuid), Seq(NotificationType.MAIL_TYPE, NotificationType.PUSH_TYPE, NotificationType.SMS_TYPE)
  )

  def removeRegistration(uuid: String)(implicit system: ActorSystem[_]) = _removeNotification(
    registrationUuid(uuid), Seq(NotificationType.MAIL_TYPE, NotificationType.PUSH_TYPE, NotificationType.SMS_TYPE)
  )

  private[this] def _removeNotification(uuid: String, channels: Seq[NotificationType] = Seq.empty)(
    implicit system: ActorSystem[_]) = {
    channels.forall{_ => notificationDao.removeNotification(uuid)}
  }

}

trait MockAccountNotifications[T <: Account] extends AccountNotifications[T] {
  override def notificationDao = MockNotificationDao
}

import Accounts._

trait AccountBehavior[T <: Account with AccountDecorator, P <: Profile]
  extends EntityBehavior[AccountCommand, T, AccountEvent, AccountCommandResult]
    with AccountNotifications[T] { self: Generator =>

  private[this] val accountKeyDao = AccountKeyDao

  protected val generator: Generator = this

  protected val rules = passwordRules()

  protected def createAccount(entityId: String, cmd: SignUp): Option[T]

  protected def createProfileUpdatedEvent(uuid: String, profile: P): ProfileUpdatedEvent[P]

  protected def createAccountCreatedEvent(account: T): AccountCreatedEvent[T]

  override protected def tagEvent(entityId: String, event: AccountEvent): Set[String] = {
    event match {
      case _: AccountCreatedEvent[_] => Set(persistenceId, s"$persistenceId-created")
      case _: AccountActivatedEvent => Set(persistenceId, s"$persistenceId-activated")
      case _: AccountDisabledEvent => Set(persistenceId, s"$persistenceId-disabled")
      case _: AccountDeletedEvent => Set(persistenceId, s"$persistenceId-deleted")
      case _: AccountDestroyedEvent => Set(persistenceId, s"$persistenceId-destroyed")
      case _: ProfileUpdatedEvent[_] => Set(persistenceId, s"$persistenceId-profile-updated")
      case _ => Set(persistenceId)
    }
  }

  override def init(system: ActorSystem[_])(implicit tTag: ClassTag[AccountCommand], m: Manifest[T]): Unit = {
    AccountKeyBehavior.init(system)
    super.init(system)
  }

  /**
    *
    * @param entityId - entity identity
    * @param state    - current state
    * @param command  - command to handle
    * @param replyTo  - optional actor to reply to
    * @return effect
    */
  override def handleCommand(
                              entityId: String,
                              state: Option[T],
                              command: AccountCommand,
                              replyTo: Option[ActorRef[AccountCommandResult]],
                              timers: TimerScheduler[AccountCommand])(implicit context: ActorContext[AccountCommand]
  ): Effect[AccountEvent, Option[T]] = {
    implicit val log = context.log
    implicit val system = context.system
    command match {

      case cmd: InitAdminAccount =>
        import cmd._
        rules.validate(password) match {
          case Left(errorCodes) => Effect.none.thenRun(_ => InvalidPassword(errorCodes) ~> replyTo)
          case Right(success) if success =>
            state match {
              case Some(account) =>
                Effect.persist[AccountEvent, Option[T]](
                  PasswordUpdatedEvent(
                    entityId,
                    encrypt(password),
                    account.verificationCode,
                    account.verificationToken
                  )
                ).thenRun(state => AdminAccountInitialized ~> replyTo)
              case _ =>
                createAccount(entityId, cmd) match {
                  case Some(account) =>
                    import account._
                    if(!secondaryPrincipals.exists((principal) => accountKeyDao.lookupAccount(principal.value).isDefined)){
                      Effect.persist[AccountEvent, Option[T]](
                        createAccountCreatedEvent(account)
                      ).thenRun(state => AdminAccountInitialized ~> replyTo)
                    }
                    else {
                      Effect.none.thenRun(_ => LoginAlreadyExists ~> replyTo)
                    }
                  case _ => Effect.none.thenRun(_ => LoginUnaccepted ~> replyTo)
              }
            }
        }

      /** handle signUp **/
      case cmd: SignUp =>
        import cmd._
        if(confirmPassword.isDefined && !password.equals(confirmPassword.get)){
          Effect.none.thenRun(_ => PasswordsNotMatched ~> replyTo)//.thenStop()
        }
        else{
          rules.validate(password) match {
            case Left(errorCodes)          => Effect.none.thenRun(_ => InvalidPassword(errorCodes) ~> replyTo)
            case Right(success) if success =>
              createAccount(entityId, cmd) match {
                case Some(account) =>
                  import account._
                  if(!secondaryPrincipals.exists((principal) => accountKeyDao.lookupAccount(principal.value).isDefined)){
                    val activationRequired = status == AccountStatus.Inactive
                    var notified = false
                    val updatedAccount =
                      if(activationRequired) { // an activation is required
                        log.info(s"activation required for ${account.primaryPrincipal.value}")
                        val activationToken = generator.generateToken(
                          account.primaryPrincipal.value,
                          ActivationTokenExpirationTime
                        )
                        accountKeyDao.addAccountKey(activationToken.token, entityId)
                        notified = sendActivation(entityId, account.asInstanceOf[T], activationToken)
                        log.info(
                          s"activation token ${if(!notified) "not " else "" }sent for ${account.primaryPrincipal.value}"
                        )
                        if(notified){
                          removeActivation(entityId)
                        }
                        if(!notified)
                          account
                            .copyWithVerificationToken(Some(activationToken))
                            .copyWithStatus(AccountStatus.PendingActivation)
                            .asInstanceOf[T]
                        else
                          account.copyWithVerificationToken(Some(activationToken)).asInstanceOf[T]
                      }
                      else{
                        account.asInstanceOf[T]
                      }
                    Effect.persist[AccountEvent, Option[T]](createAccountCreatedEvent(updatedAccount))
                      .thenRun(
                        _ =>
                          if(activationRequired && !notified) {
                            UndeliveredActivationToken
                          }
                          else {
                            if(updatedAccount.status == AccountStatus.Active){
                              if(sendRegistration(entityId, updatedAccount)){
                                removeRegistration(entityId)
                              }
                            }
                            AccountCreated(updatedAccount)
                          } ~> replyTo
                      )
                  }
                  else {
                    Effect.none.thenRun(_ => LoginAlreadyExists ~> replyTo)
                  }
                case _             => Effect.none.thenRun(_ => LoginUnaccepted ~> replyTo)
              }
          }
        }

      /** handle account activation **/
      case cmd: Activate =>
        import cmd._
        state match {
          case Some(account) if account.status == AccountStatus.Inactive =>
            import account._
            verificationToken match {
              case Some(v) =>
                if(v.expired){
                  accountKeyDao.removeAccountKey(v.token)
                  val activationToken = generator.generateToken(
                    account.primaryPrincipal.value, ActivationTokenExpirationTime
                  )
                  accountKeyDao.addAccountKey(activationToken.token, entityId)
                  val notified = sendActivation(entityId, account, activationToken)
                  log.info(s"activation token ${if(!notified) "not " else "" }sent for ${account.primaryPrincipal.value}")
                  if(notified){
                    removeActivation(entityId)
                  }
                  Effect.persist[AccountEvent, Option[T]](
                    VerificationTokenAdded(
                      entityId,
                      activationToken
                    )
                  ).thenRun(_ => TokenExpired ~> replyTo)
                }
                else if(v.token != token){
                  Effect.none.thenRun(_ => InvalidToken ~> replyTo)
                }
                else{
                  Effect.persist[AccountEvent, Option[T]](AccountActivatedEvent(entityId))
                    .thenRun(state => AccountActivated(state.getOrElse(account)) ~> replyTo)
                }
              case _       => Effect.none.thenRun(_ => TokenNotFound ~> replyTo)
            }
          case None    => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
          case _       => Effect.none.thenRun(_ => IllegalStateError ~> replyTo)
        }

      /** handle login **/
      case cmd: Login   =>
        import cmd._
        state match {
          case Some(account) if account.status == AccountStatus.Active =>
            val checkLogin = account.principals.exists(_.value == login) //check login against account principal
            if(checkLogin && checkEncryption(account.credentials, password)){
              Effect.persist[AccountEvent, Option[T]](
                LoginSucceeded(
                  entityId,
                  now()
                )
              ).thenRun(state => new LoginSucceededResult(state.get) ~> replyTo)
            }
            else if(!checkLogin){
              Effect.none.thenRun(_ => LoginAndPasswordNotMatched ~> replyTo)
            }
            else { // wrong password
              val nbLoginFailures = account.nbLoginFailures + 1
              val disabled = nbLoginFailures > maxLoginFailures // disable account
              Effect.persist[AccountEvent, Option[T]](
                if(disabled)
                  AccountDisabledEvent(
                    entityId,
                    nbLoginFailures
                  )
                else
                  LoginFailed(
                    entityId,
                    nbLoginFailures
                  )
              )
                .thenRun(state => {
                  if(disabled){
                    if(account.status != AccountStatus.Disabled){
                      log.info(s"reset password required for ${account.primaryPrincipal.value}")
                      sendAccountDisabled(entityId, account)
                    }
                    AccountDisabled
                  }
                  else{
                    log.info(s"$nbLoginFailures login failure(s) for ${account.primaryPrincipal.value}")
                    LoginAndPasswordNotMatched
                  }
                } ~> replyTo)
            }
          case Some(account) if account.status == AccountStatus.Disabled =>
            log.info(s"reset password required for ${account.primaryPrincipal.value}")
            sendAccountDisabled(entityId, account)
            Effect.none.thenRun(_ => AccountDisabled ~> replyTo)
          case None                                                      =>
            Effect.none.thenRun(_ => LoginAndPasswordNotMatched ~> replyTo) //WrongLogin
          case _                                                         =>
            Effect.none.thenRun(_ => IllegalStateError ~> replyTo)
        }

      /** handle send verification code **/
      case cmd: SendVerificationCode =>
        import cmd._
        if(EmailValidator.check(principal) || GsmValidator.check(principal)){
          state match {
            case Some(account) if account.principals.exists(_.value == principal) =>
              account.verificationCode.foreach((v) => accountKeyDao.removeAccountKey(v.code))
              val verificationCode = generator.generatePinCode(VerificationCodeSize, VerificationCodeExpirationTime)
              accountKeyDao.addAccountKey(verificationCode.code, entityId)
              val notified = sendVerificationCode(entityId, account, verificationCode)
              Effect.persist[AccountEvent, Option[T]](
                VerificationCodeAdded(
                  entityId,
                  verificationCode
                )
              ).thenRun(_ => (if(notified) VerificationCodeSent else UndeliveredVerificationCode) ~> replyTo)
            case _ => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
          }
        }
        else{
          Effect.none.thenRun(_ => InvalidPrincipal ~> replyTo)
        }

      case cmd: SendResetPasswordToken =>
        import cmd._
        if(EmailValidator.check(principal) || GsmValidator.check(principal)){
          state match {
            case Some(account) if account.principals.exists(_.value == principal) =>
              account.verificationToken.foreach((v) => accountKeyDao.removeAccountKey(v.token))
              val verificationToken = generator.generateToken(account.primaryPrincipal.value, VerificationTokenExpirationTime)
              accountKeyDao.addAccountKey(verificationToken.token, entityId)
              val notified = sendResetPassword(entityId, account, verificationToken)
              Effect.persist[AccountEvent, Option[T]](
                VerificationTokenAdded(
                  entityId,
                  verificationToken
                )
              ).thenRun(_ => (if(notified) ResetPasswordTokenSent else UndeliveredResetPasswordToken) ~> replyTo)
            case _ => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
          }
        }
        else{
          Effect.none.thenRun(_ => InvalidPrincipal ~> replyTo)
        }

      case cmd: CheckResetPasswordToken =>
        import cmd._
        state match {
          case Some(account) =>
            import account._
            verificationToken match {
              case Some(v) =>
                if(v.expired){
                  accountKeyDao.removeAccountKey(v.token)
                  val verificationToken = generator.generateToken(
                    account.primaryPrincipal.value, ActivationTokenExpirationTime
                  )
                  accountKeyDao.addAccountKey(verificationToken.token, entityId)
                  val notified = sendResetPassword(entityId, account, verificationToken)
                  Effect.persist[AccountEvent, Option[T]](
                    VerificationTokenAdded(
                      entityId,
                      verificationToken
                    )
                  ).thenRun(_ => (if(notified) NewResetPasswordTokenSent else UndeliveredResetPasswordToken) ~> replyTo)
                }
                else{
                  if(v.token != token){
                    log.warn(s"tokens do not match !!!!!")
                  }
                  Effect.none.thenRun(_ => ResetPasswordTokenChecked ~> replyTo)
                }
              case _       => Effect.none.thenRun(_ => TokenNotFound ~> replyTo)
            }
          case _             => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
        }

      case cmd: ResetPassword =>
        import cmd._
        val _confirmedPassword = confirmedPassword.getOrElse(newPassword)
        if(!newPassword.equals(_confirmedPassword)){
          Effect.none.thenRun(_ => PasswordsNotMatched ~> replyTo)
        }
        else {
          rules.validate(newPassword) match {
            case Left(errorCodes) => Effect.none.thenRun(_ => InvalidPassword(errorCodes) ~> replyTo)
            case Right(success) if success =>
              state match {
                case Some(account) =>
                  import account._
                  if (NotificationsConfig.resetPasswordCode) {
                    verificationCode match {
                      case Some(verification) =>
                        if (!verification.expired) {
                          Effect.persist[AccountEvent, Option[T]](
                            PasswordUpdatedEvent(
                              entityId,
                              encrypt(newPassword),
                              None,
                              account.verificationToken
                            )
                          ).thenRun(_ => {
                            accountKeyDao.removeAccountKey(verification.code)
                            PasswordReseted(entityId)
                          } ~> replyTo)
                        }
                        else {
                          Effect.none.thenRun(_ => CodeExpired ~> replyTo)
                        }
                      case _ => Effect.none.thenRun(_ => CodeNotFound ~> replyTo)
                    }
                  }
                  else {
                    verificationToken match {
                      case Some(verification) =>
                        if (!verification.expired) {
                          Effect.persist[AccountEvent, Option[T]](
                            PasswordUpdatedEvent(
                              entityId,
                              encrypt(newPassword),
                              account.verificationCode,
                              None
                            )
                          ).thenRun(_ => {
                              accountKeyDao.removeAccountKey(verification.token)
                              PasswordReseted(entityId)
                            } ~> replyTo)
                        }
                        else {
                          Effect.none.thenRun(_ => TokenExpired ~> replyTo)
                        }
                      case _ => Effect.none.thenRun(_ => TokenNotFound ~> replyTo)
                    }
                  }
                case _ => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
              }
          }
        }

      /** handle update password **/
      case cmd: UpdatePassword =>
        import cmd._
        val _confirmedPassword = confirmedPassword.getOrElse(newPassword)
        if(!newPassword.equals(_confirmedPassword)){
          Effect.none.thenRun(_ => PasswordsNotMatched ~> replyTo)
        }
        else{
          rules.validate(newPassword) match {
            case Left(errorCodes)          => Effect.none.thenRun(_ => InvalidPassword(errorCodes) ~> replyTo)
            case Right(success) if success =>
              state match {
                case Some(account) =>
                  import account._
                  if(checkEncryption(credentials, oldPassword)){
                    Effect.persist[AccountEvent, Option[T]](
                      PasswordUpdatedEvent(
                        entityId,
                        encrypt(newPassword),
                        account.verificationCode,
                        account.verificationToken
                      )
                    ).thenRun(state => {
                        sendPasswordUpdated(entityId, state.get)
                        PasswordUpdated(state.get)
                      } ~> replyTo)
                  }
                  else {
                    Effect.none.thenRun(_ => LoginAndPasswordNotMatched ~> replyTo)
                  }
                case _       => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
              }
          }
        }

      /**
        * handle device registration
        */
      case cmd: RegisterDevice =>
        Effect.persist[AccountEvent, Option[T]](
          DeviceRegisteredEvent(
            entityId,
            cmd.registration
          )
        ).thenRun(_ => DeviceRegistered ~> replyTo)

      /**
        * handle device unregistration
        */
      case cmd: UnregisterDevice =>
        state match {
          case Some(account) =>
            account.registrations.find(_.regId == cmd.regId) match {
              case Some(r) =>
                Effect.persist[AccountEvent, Option[T]](
                  DeviceUnregisteredEvent(
                    entityId,
                    r
                  )
                ).thenRun(_ => DeviceUnregistered ~> replyTo)
              case _       => Effect.none.thenRun(_ => DeviceRegistrationNotFound ~> replyTo)
            }
          case _       => Effect.none.thenRun(_ => DeviceRegistrationNotFound ~> replyTo)
        }

      /** handle unsubscribe **/
      case cmd: Unsubscribe        =>
        state match {
          case Some(account) =>
            Effect.persist[AccountEvent, Option[T]](
              AccountDeletedEvent(entityId)
            ).thenRun(state => AccountDeleted(state.get) ~> replyTo)
          case _ => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
        }

      case _: DestroyAccount.type =>
        state match {
          case Some(account) =>
            Effect.persist[AccountEvent, Option[T]](
              AccountDestroyedEvent(entityId)
            ).thenRun(_ => AccountDestroyed(entityId) ~> replyTo)
          case _ => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
        }

      case _: Logout.type    => Effect.none.thenRun(_ => LogoutSucceeded ~> replyTo)

      case cmd: UpdateProfile  =>
        import cmd._
        state match {
          case Some(account) =>
            val phoneNumber = profile.phoneNumber.getOrElse("").trim
            val email = profile.email.getOrElse("").trim
            if(phoneNumber.length > 0 && accountKeyDao.lookupAccount(phoneNumber).getOrElse(entityId) != entityId){
              Effect.none.thenRun(_ => GsmAlreadyExists ~> replyTo)
            }
            else if(email.length > 0 && accountKeyDao.lookupAccount(email).getOrElse(entityId) != entityId){
              Effect.none.thenRun(_ => EmailAlreadyExists ~> replyTo)
            }
            else{
              Effect.persist[AccountEvent, Option[T]](
                createProfileUpdatedEvent(
                  entityId,
                  account.completeProfile(profile).asInstanceOf[P]
                )
              ).thenRun(_ => {
                ProfileUpdated
              } ~> replyTo)
            }
          case _             => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
        }

      case cmd: SwitchProfile  =>
        import cmd._
        state match {
          case Some(account) =>
            Effect.persist[AccountEvent, Option[T]](
              ProfileSwitchedEvent(
                entityId,
                name
              )
            ).thenRun(_ => {
              ProfileSwitched(account.profile(Some(name)))
            } ~> replyTo)
          case _             => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
        }

      case cmd: LoadProfile =>
        import cmd._
        state match {
          case Some(account) =>
            account.profile(name) match {
              case Some(profile) => Effect.none.thenRun(_ => ProfileLoaded(profile) ~> replyTo)
              case _             => Effect.none.thenRun(_ => ProfileNotFound ~> replyTo)
            }
          case _             => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
        }

      /** no handlers **/
      case _ => super.handleCommand(entityId, state, command, replyTo, timers)

    }
  }

  /**
    *
    * @param state - current state
    * @param event - event to hanlde
    * @return new state
    */
  override def handleEvent(state: Option[T], event: AccountEvent)(
    implicit context: ActorContext[AccountCommand]): Option[T] = {
    event match {
      case evt: AccountCreatedEvent[_] =>
        val account = evt.document
        account.secondaryPrincipals.foreach((principal) =>
          accountKeyDao.addAccountKey(principal.value, account.uuid)
        )
        Some(account.asInstanceOf[T])

      case evt: AccountActivatedEvent =>
        state.map(_
          .copyWithStatus(AccountStatus.Active)
          .copyWithVerificationToken(None)
          .copyWithLastUpdated()
          .asInstanceOf[T]
        )

      case evt: AccountDisabledEvent =>
        import evt._
        state.map(_
          .copyWithStatus(AccountStatus.Disabled)
          .copyWithNbLoginFailures(nbLoginFailures)
          .copyWithLastUpdated()
          .asInstanceOf[T]
        )

      case evt: AccountDeletedEvent =>
        state.map(_
          .copyWithStatus(AccountStatus.Deleted)
          .copyWithLastUpdated()
          .asInstanceOf[T]
        )

      case evt: AccountDestroyedEvent =>
        state match {
          case Some(account) =>
            account.principals.foreach((principal) =>
              accountKeyDao.removeAccountKey(principal.value)
            )
          case _ =>
        }
        emptyState

      case evt: ProfileUpdatedEvent[_] =>
        import evt._
        state match {
          case Some(account) =>
            account.secondaryPrincipals.foreach((principal) =>
              accountKeyDao.removeAccountKey(principal.value)
            )
            account.secondaryPrincipals.foreach((principal) =>
              accountKeyDao.addAccountKey(principal.value, uuid)
            )
            Some(
              account
                .add(profile)
                .copyWithLastUpdated()
                .asInstanceOf[T]
            )
          case _             => state
        }

      case evt: DeviceRegisteredEvent =>
        import evt._
        state.map(account =>
          account.copyWithRegistrations(
            account.registrations.filterNot(_.regId == registration.regId).+:(registration)
          ).asInstanceOf[T]
        )

      case evt: DeviceUnregisteredEvent =>
        import evt._
        state.map(account =>
          account.copyWithRegistrations(
            account.registrations.filterNot(_.regId == registration.regId)
          ).asInstanceOf[T]
        )

      case evt: VerificationTokenAdded =>
        import evt._
        state.map(_.copyWithVerificationToken(Some(token)).copyWithLastUpdated().asInstanceOf[T])

      case evt: VerificationCodeAdded =>
        import evt._
        state.map(_.copyWithVerificationCode(Some(code)).copyWithLastUpdated().asInstanceOf[T])

      case evt: ProfileSwitchedEvent =>
        import evt._
        state.map(_.setCurrentProfile(name).copyWithLastUpdated().asInstanceOf[T])

      case evt: LoginSucceeded =>
        import evt._
        state.map(_
          .copyWithNbLoginFailures(0)// reset number of login failures
          .copyWithLastLogin(Some(lastLogin))
          .copyWithLastUpdated()
          .asInstanceOf[T]
        )

      case evt: LoginFailed =>
        import evt._
        state.map(_
          .copyWithNbLoginFailures(nbLoginFailures)
          .copyWithLastUpdated()
          .asInstanceOf[T]
        )

      case evt: PasswordUpdatedEvent =>
        import evt._
        state.map(_
          .copyWithCredentials(credentials)
          .copyWithVerificationCode(code)
          .copyWithVerificationToken(token)
          .copyWithStatus(AccountStatus.Active) //TODO check this
          .copyWithNbLoginFailures(0)
          .copyWithLastUpdated()
          .asInstanceOf[T]
        )

      case _ => super.handleEvent(state, event)
    }
  }
}

trait BasicAccountBehavior extends AccountBehavior[BasicAccount, BasicAccountProfile] { self: Generator =>
  override protected def createAccount(entityId: String, cmd: SignUp): Option[BasicAccount] =
    BasicAccount(cmd, Some(entityId))

  override protected def createProfileUpdatedEvent(uuid: String, profile: BasicAccountProfile) =
    BasicAccountProfileUpdatedEvent(uuid, profile)

  override protected def createAccountCreatedEvent(account: BasicAccount): AccountCreatedEvent[BasicAccount] =
    BasicAccountCreatedEvent(account)
}

object BasicAccountBehavior extends BasicAccountBehavior
  with DefaultGenerator {
  override def persistenceId: String = "Account"

  override def init(system: ActorSystem[_])(
    implicit tTag: ClassTag[AccountCommand], m: Manifest[BasicAccount]): Unit = {
    AllNotificationsBehavior.init(system)
    super.init(system)
  }
}

object MockBasicAccountBehavior extends BasicAccountBehavior
  with MockGenerator
  with MockAccountNotifications[BasicAccount] {
  override def persistenceId: String = "MockAccount"

  override def init(system: ActorSystem[_])(
    implicit tTag: ClassTag[AccountCommand], m: Manifest[BasicAccount]): Unit = {
    MockAllNotificationsBehavior.init(system)
    super.init(system)
  }
}

trait AccountKeyBehavior extends EntityBehavior[
  AccountKeyCommand,
  AccountKeyState,
  AccountEvent,
  AccountKeyCommandResult]{

  override def persistenceId: String = "AccountKey"

  /**
    *
    * @param entityId - entity identity
    * @param state    - current state
    * @param command  - command to handle
    * @param replyTo  - optional actor to reply to
    * @return effect
    */
  override def handleCommand(
                              entityId: String,
                              state: Option[AccountKeyState],
                              command: AccountKeyCommand,
                              replyTo: Option[ActorRef[AccountKeyCommandResult]],
                              timers: TimerScheduler[AccountKeyCommand])(
                              implicit context: ActorContext[AccountKeyCommand]
  ): Effect[AccountEvent, Option[AccountKeyState]] = {
    command match {

      case cmd: AddAccountKey =>
        Effect.persist(
          AccountKeyAdded(entityId, cmd.account)
        ).thenRun(
          _ => AccountKeyAdded(entityId, cmd.account) ~> replyTo
        )

      case RemoveAccountKey =>
        Effect.persist(
          AccountKeyRemoved(
            entityId
          )
        ).thenRun(
          _ => AccountKeyRemoved(entityId) ~> replyTo
        )//.thenStop()

      case LookupAccountKey =>
        state match {
          case Some(s) => Effect.none.thenRun(_ => AccountKeyFound(s.account) ~> replyTo)
          case _       => Effect.none.thenRun(_ => AccountKeyNotFound ~> replyTo)
        }

      case _ => super.handleCommand(entityId, state, command, replyTo, timers)
    }
  }

  /**
    *
    * @param state - current state
    * @param event - event to hanlde
    * @return new state
    */
  override def handleEvent(state: Option[AccountKeyState], event: AccountEvent)(
    implicit context: ActorContext[AccountKeyCommand]): Option[AccountKeyState] = {
    event match {
      case e: AccountKeyAdded => Some(AccountKeyState(e.uuid, e.account))
      case _: AccountKeyRemoved => emptyState
      case _ => super.handleEvent(state, event)
    }
  }
}

object AccountKeyBehavior extends AccountKeyBehavior
