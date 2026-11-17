package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.*
import com.example.util.Formatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `app name and database id resources are configured`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SchoolManager", appName)
    assertTrue(context.getString(R.string.firestore_database_id).isNotBlank())
  }

  @Test
  fun `all eight rbac roles including eleve are registered`() {
    val names = UserRole.entries.map { it.name }.toSet()
    assertEquals(
      setOf(
        "SUPER_ADMIN",
        "ADMIN_ETABLISSEMENT",
        "DIRECTEUR",
        "SECRETAIRE",
        "COMPTABLE",
        "ENSEIGNANT",
        "ELEVE",
        "PARENT"
      ),
      names
    )
    assertEquals(UserRole.ELEVE, UserRole.fromRaw("ELEVE"))
    assertEquals("Élève", UserRole.ELEVE.label)
  }

  @Test
  fun `weighted averages ranking and fcfa balances compute accurately`() {
    val stu1 = Student(
      id = "s1",
      firstName = "Amina",
      lastName = "Ndi",
      classId = "c1",
      className = "3ème A",
      tuitionTotalFcfa = 150_000L
    )
    val stu2 = Student(
      id = "s2",
      firstName = "Brice",
      lastName = "Kamga",
      classId = "c1",
      className = "3ème A",
      tuitionTotalFcfa = 150_000L
    )
    val grades = listOf(
      Grade(studentId = "s1", classId = "c1", subjectId = "math", subjectName = "Maths", coefficient = 4L, term = TermPeriod.TRIMESTRE_1.name, score = 16.0, maxScore = 20.0),
      Grade(studentId = "s1", classId = "c1", subjectId = "fr", subjectName = "Français", coefficient = 2L, term = TermPeriod.TRIMESTRE_1.name, score = 13.0, maxScore = 20.0),
      Grade(studentId = "s2", classId = "c1", subjectId = "math", subjectName = "Maths", coefficient = 4L, term = TermPeriod.TRIMESTRE_1.name, score = 10.0, maxScore = 20.0)
    )
    val reports = Formatters.computeTermReports(listOf(stu1, stu2), grades, "c1", TermPeriod.TRIMESTRE_1)
    assertEquals(2, reports.size)
    assertEquals("s1", reports[0].student.id)
    assertEquals(1, reports[0].rank)
    assertEquals(15.0, reports[0].weightedAverage, 0.01)

    val payments = listOf(
      Payment(studentId = "s1", amountFcfa = 90_000L, providerStatus = ProviderStatus.CONFIRME.name),
      Payment(studentId = "s2", amountFcfa = 50_000L, providerStatus = ProviderStatus.EN_ATTENTE.name)
    )
    val balances = Formatters.computeStudentBalances(listOf(stu1, stu2), payments)
    val bal1 = balances.first { it.student.id == "s1" }
    val bal2 = balances.first { it.student.id == "s2" }
    assertEquals(60_000L, bal1.remainingFcfa)
    assertEquals(150_000L, bal2.remainingFcfa)
  }

  @Test
  fun `unique matricules avoid duplicates across categories`() {
    val existing = mutableListOf<String>()
    val generated = (1..25).map { idx ->
      val cat = if (idx % 3 == 0) SchoolCategory.UNIVERSITE_INSTITUT
      else if (idx % 2 == 0) SchoolCategory.PRIMAIRE_MATERNELLE
      else SchoolCategory.COLLEGE_LYCEE
      val mat = Formatters.generateUniqueMatricule(
        schoolName = "Institut Polyvalent",
        category = cat,
        academicYear = "2026-2027",
        existingMatricules = existing
      )
      existing.add(mat)
      mat
    }
    assertEquals(25, generated.toSet().size)
    assertTrue(generated.any { it.contains("-SC-2026-") })
    assertTrue(generated.any { it.contains("-PR-2026-") })
    assertTrue(generated.any { it.contains("-UN-2026-") })
  }

  @Test
  fun `verification hash matches exact identity and rejects wrong birthDate or pin`() {
    val pin = Formatters.generateStudentAccessPin("seed-test-001")
    assertEquals(6, pin.length)
    val expected = Formatters.computeStudentVerificationHash("sch_1", "INS-UN-2026-0001", "2005-04-19", pin)
    val sameCaseInsensitive = Formatters.computeStudentVerificationHash("sch_1", "ins-un-2026-0001", "2005-04-19", pin)
    val wrongPin = Formatters.computeStudentVerificationHash("sch_1", "INS-UN-2026-0001", "2005-04-19", "999999")
    val wrongBirth = Formatters.computeStudentVerificationHash("sch_1", "INS-UN-2026-0001", "2005-04-20", pin)
    assertEquals(expected, sameCaseInsensitive)
    assertTrue(expected != wrongPin)
    assertTrue(expected != wrongBirth)
  }

  @Test
  fun `annual results calculation handles secondary ranking and university lmd compensation`() {
    // 1. Collège / Lycée
    val secSchool = School(
      id = "sch_sec",
      name = "Lycée de Biyem-Assi",
      category = SchoolCategory.COLLEGE_LYCEE.name,
      passingAverage = 10.0,
      academicYear = "2026-2027"
    )
    val secStudents = listOf(
      Student(id = "st1", schoolId = "sch_sec", matricule = "LYC-SC-2026-0001", firstName = "Paul", lastName = "Mbia", birthDate = "2010-01-10", classId = "c1", className = "3ème", level = "3ème"),
      Student(id = "st2", schoolId = "sch_sec", matricule = "LYC-SC-2026-0002", firstName = "Eva", lastName = "Bello", birthDate = "2010-02-11", classId = "c1", className = "3ème", level = "3ème")
    )
    val secGrades = listOf(
      Grade(schoolId = "sch_sec", studentId = "st1", classId = "c1", subjectId = "m1", subjectName = "Maths", coefficient = 4, term = TermPeriod.TRIMESTRE_3.name, score = 15.0, maxScore = 20.0),
      Grade(schoolId = "sch_sec", studentId = "st2", classId = "c1", subjectId = "m1", subjectName = "Maths", coefficient = 4, term = TermPeriod.TRIMESTRE_3.name, score = 7.0, maxScore = 20.0)
    )
    val secResults = Formatters.computeAnnualResults(secSchool, secStudents, emptyList(), secGrades, emptyList())
    assertEquals(2, secResults.size)
    val r1 = secResults.first { it.studentId == "st1" }
    val r2 = secResults.first { it.studentId == "st2" }
    assertEquals(1L, r1.rank)
    assertEquals(ResultDecision.ADMIS.name, r1.decision)
    assertEquals(ResultDecision.REDOUBLEMENT.name, r2.decision)

    // 2. Université LMD avec compensation
    val univSchool = School(
      id = "sch_univ",
      name = "Université Numérique",
      category = SchoolCategory.UNIVERSITE_INSTITUT.name,
      passingAverage = 10.0,
      creditPassThreshold = 10.0,
      annualCreditsRequired = 60L,
      lmdCompensation = true,
      academicYear = "2026-2027"
    )
    val univStu = Student(
      id = "u1",
      schoolId = "sch_univ",
      matricule = "UNI-UN-2026-0001",
      firstName = "Claire",
      lastName = "Ngo",
      birthDate = "2003-07-15",
      classId = "l3",
      className = "L3 Info",
      level = "Licence 3",
      faculty = "Sciences",
      department = "Informatique",
      program = "Génie Logiciel"
    )
    val ues = listOf(
      Subject(id = "ue1", schoolId = "sch_univ", name = "Génie Logiciel", code = "INF301", credits = 30L, semester = "S1", classId = "l3", universityUe = true),
      Subject(id = "ue2", schoolId = "sch_univ", name = "Systèmes Distribués", code = "INF302", credits = 30L, semester = "S2", classId = "l3", universityUe = true)
    )
    val uGrades = listOf(
      Grade(schoolId = "sch_univ", studentId = "u1", classId = "l3", subjectId = "ue1", subjectName = "Génie Logiciel", coefficient = 3, term = TermPeriod.SEMESTRE_1.name, score = 14.0, maxScore = 20.0),
      Grade(schoolId = "sch_univ", studentId = "u1", classId = "l3", subjectId = "ue2", subjectName = "Systèmes Distribués", coefficient = 3, term = TermPeriod.SEMESTRE_2.name, score = 9.0, maxScore = 20.0)
    )
    val univResults = Formatters.computeAnnualResults(univSchool, listOf(univStu), ues, uGrades, emptyList())
    assertEquals(1, univResults.size)
    val ur = univResults.first()
    assertEquals(11.5, ur.annualAverage, 0.01)
    assertEquals(60L, ur.creditsObtained)
    assertEquals(ResultDecision.VALIDE_LMD.name, ur.decision)
  }

  @Test
  fun `scheduled results remain hidden until publish timestamp and visible once published`() {
    val now = 1_700_000_000_000L
    val draft = AnnualResult(status = ResultStatus.BROUILLON.name)
    val validated = AnnualResult(status = ResultStatus.VALIDE.name)
    val scheduledFuture = AnnualResult(status = ResultStatus.PROGRAMME.name, scheduledPublishAtMillis = now + 60_000L)
    val scheduledDue = AnnualResult(status = ResultStatus.PROGRAMME.name, scheduledPublishAtMillis = now - 1_000L)
    val published = AnnualResult(status = ResultStatus.PUBLIE.name)

    assertTrue(!Formatters.isResultVisibleToLearner(draft, now))
    assertTrue(!Formatters.isResultVisibleToLearner(validated, now))
    assertTrue(!Formatters.isResultVisibleToLearner(scheduledFuture, now))
    assertTrue(Formatters.isResultVisibleToLearner(scheduledDue, now))
    assertTrue(Formatters.isResultVisibleToLearner(published, now))
  }
}
