package xarvalus.reactive.stock.common

import com.lightbend.lagom.scaladsl.api.transport.{ExceptionMessage, TransportErrorCode, TransportException}
import com.wix.accord.transform.ValidationTransform
import com.wix.accord.{Descriptions, Failure, Violation, validate => accordValidate}

object ValidationUtil {
  def validate[A](underValidation: A)(implicit validator: ValidationTransform.TransformedValidator[A]) = {
    val validationResult = accordValidate(underValidation)

    validationResult match {
      case failure: Failure =>
        throw ValidationException(
          underValidation, "Object failed validation", extractValidationErrors(failure.violations))
      case _ =>
    }
  }
  private def extractValidationErrors(violations: Set[Violation]) = {
    violations.map(violation => {
      ValidationError(
        key = Descriptions.render(violation.path),
        message = violation.constraint
      )
    })
  }
}

case class ValidationException[A](
  validatedObject: A,
  message: String,
  errors: Set[ValidationError]
) extends TransportException(
  TransportErrorCode.BadRequest, ValidationException.generateMessage(message, errors), null)

object ValidationException {
  def generateMessage(message: String, errors: Set[ValidationError]): ExceptionMessage = {
    val details = s"$message\n" + generateErrors(errors)

    new ExceptionMessage("ValidationException", details)
  }

  private def generateErrors(errors: Set[ValidationError]) = {
    errors.map(error => s"${error.key}: ${error.message}\n").mkString("- ", "- ", "")
  }
}

case class ValidationError(key: String, message: String)
