package io.edupilot.auth.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Documented
@Constraint(validatedBy = {})
@NotBlank(message = "비밀번호는 필수입니다.")
@Pattern(
	regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$",
	message = "비밀번호는 8~64자이며 영문과 숫자를 각각 하나 이상 포함해야 합니다."
)
@Target({
	ElementType.FIELD,
	ElementType.METHOD,
	ElementType.PARAMETER,
	ElementType.ANNOTATION_TYPE,
	ElementType.RECORD_COMPONENT
})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {

	String message() default "비밀번호를 확인해 주세요.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
