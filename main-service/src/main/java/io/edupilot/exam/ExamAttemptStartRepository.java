package io.edupilot.exam;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamAttemptStartRepository extends JpaRepository<ExamAttemptStart, Long> {

	Optional<ExamAttemptStart> findByExam_IdAndUser_Id(Long examId, Long userId);

	long countByExam_IdAndUser_Id(Long examId, Long userId);
}
