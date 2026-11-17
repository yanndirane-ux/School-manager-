package com.example.util

import com.example.model.*
import com.google.firebase.Timestamp
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object Formatters {
    private val frenchLocale = Locale.FRENCH
    private val numberFormat = NumberFormat.getIntegerInstance(frenchLocale)
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", frenchLocale)
    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", frenchLocale)

    fun fcfa(amount: Long): String = "${numberFormat.format(amount)} FCFA"

    fun todayIso(): String = isoDateFormat.format(Date())

    fun formatTimestamp(ts: Timestamp?): String {
        val date = ts?.toDate() ?: return "À l'instant"
        return dateTimeFormat.format(date)
    }

    fun formatDateMillis(millis: Long): String {
        if (millis <= 0L) return "—"
        return dateFormat.format(Date(millis))
    }

    fun formatDateTimeMillis(millis: Long): String {
        if (millis <= 0L) return "—"
        return dateTimeFormat.format(Date(millis))
    }

    fun parseDateTimeMillis(dateIso: String, timeHm: String): Long? {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { isLenient = false }
            sdf.parse("${dateIso.trim()} ${timeHm.trim()}")?.time
        } catch (_: Exception) {
            null
        }
    }

    fun sha256Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun generateStudentAccessPin(seed: String = System.nanoTime().toString()): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
        val num = ((digest[0].toInt() and 0xFF) shl 16) or
            ((digest[1].toInt() and 0xFF) shl 8) or
            (digest[2].toInt() and 0xFF)
        val sixDigits = 100_000 + (num % 900_000)
        return sixDigits.toString()
    }

    fun computeStudentVerificationHash(
        schoolId: String,
        matricule: String,
        birthDate: String,
        pin: String
    ): String {
        val normalized = listOf(
            schoolId.trim(),
            matricule.trim().uppercase(Locale.ROOT),
            birthDate.trim(),
            pin.trim().uppercase(Locale.ROOT)
        ).joinToString("|")
        return sha256Hex(normalized)
    }

    fun generateMatricule(schoolName: String, existingCount: Int): String {
        val prefix = schoolName.uppercase(Locale.ROOT)
            .filter { it.isLetter() }
            .take(3)
            .ifBlank { "SCH" }
        val year = SimpleDateFormat("yyyy", Locale.US).format(Date())
        val seq = (existingCount + 1).toString().padStart(4, '0')
        return "$prefix-$year-$seq"
    }

    fun generateUniqueMatricule(
        schoolName: String,
        category: SchoolCategory,
        academicYear: String,
        existingMatricules: Collection<String>
    ): String {
        val existingUpper = existingMatricules.map { it.trim().uppercase(Locale.ROOT) }.toSet()
        val schoolPrefix = schoolName.uppercase(Locale.ROOT)
            .filter { it.isLetter() }
            .take(3)
            .ifBlank { "SCH" }
        val catCode = when (category) {
            SchoolCategory.PRIMAIRE_MATERNELLE -> "PR"
            SchoolCategory.COLLEGE_LYCEE -> "SC"
            SchoolCategory.UNIVERSITE_INSTITUT -> "UN"
        }
        val yearCode = academicYear.filter { it.isDigit() }.take(4).ifBlank {
            SimpleDateFormat("yyyy", Locale.US).format(Date())
        }
        var seq = existingUpper.size + 1
        while (true) {
            val candidate = "$schoolPrefix-$catCode-$yearCode-${seq.toString().padStart(4, '0')}"
            if (candidate.uppercase(Locale.ROOT) !in existingUpper) return candidate
            seq++
        }
    }

    fun generateEmployeeCode(existingCount: Int): String {
        val year = SimpleDateFormat("yy", Locale.US).format(Date())
        val seq = (existingCount + 1).toString().padStart(3, '0')
        return "ENS-$year-$seq"
    }

    fun generateReceiptNumber(existingCount: Int): String {
        val yearMonth = SimpleDateFormat("yyyyMM", Locale.US).format(Date())
        val seq = (existingCount + 1).toString().padStart(4, '0')
        return "REC-$yearMonth-$seq"
    }

    fun generateDocRef(prefix: String, existingCount: Int): String {
        val yearMonth = SimpleDateFormat("yyyyMM", Locale.US).format(Date())
        val seq = (existingCount + 1).toString().padStart(4, '0')
        return "${prefix.uppercase(Locale.US)}-$yearMonth-$seq"
    }

    fun mentionForAverage(avg: Double): String = when {
        avg >= 16.0 -> "Très Bien"
        avg >= 14.0 -> "Bien"
        avg >= 12.0 -> "Assez Bien"
        avg >= 10.0 -> "Passable"
        avg >= 8.0 -> "Insuffisant"
        else -> "Faible"
    }

    fun round2(value: Double): Double = (value * 100.0).roundToInt() / 100.0

    fun serializeSubjectLines(lines: List<ResultSubjectLine>): String {
        val arr = JSONArray()
        lines.forEach { line ->
            arr.put(
                JSONObject().apply {
                    put("code", line.code)
                    put("name", line.name)
                    put("coefficient", line.coefficient)
                    put("credits", line.credits)
                    put("scoreOn20", round2(line.scoreOn20))
                    put("semester", line.semester)
                    put("validated", line.validated)
                    put("appreciation", line.appreciation)
                }
            )
        }
        return arr.toString()
    }

    fun parseSubjectLines(json: String): List<ResultSubjectLine> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        ResultSubjectLine(
                            code = obj.optString("code", ""),
                            name = obj.optString("name", ""),
                            coefficient = obj.optLong("coefficient", 1L),
                            credits = obj.optLong("credits", 0L),
                            scoreOn20 = obj.optDouble("scoreOn20", 0.0),
                            semester = obj.optString("semester", ""),
                            validated = obj.optBoolean("validated", false),
                            appreciation = obj.optString("appreciation", "")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun isResultVisibleToLearner(result: AnnualResult, nowMillis: Long = System.currentTimeMillis()): Boolean {
        return when (ResultStatus.fromRaw(result.status)) {
            ResultStatus.PUBLIE -> true
            ResultStatus.PROGRAMME -> result.scheduledPublishAtMillis in 1..nowMillis
            else -> false
        }
    }

    fun computeTermReports(
        students: List<Student>,
        grades: List<Grade>,
        classId: String,
        term: TermPeriod
    ): List<StudentTermReport> {
        val classStudents = students.filter { it.classId == classId }
        val rawReports = classStudents.map { student ->
            val stuGrades = grades.filter { it.studentId == student.id && it.term == term.name }
            val bySubject = stuGrades.groupBy { it.subjectId.ifBlank { it.subjectName } }
            val subjectAvgs = bySubject.map { (subjKey, list) ->
                val first = list.first()
                val avg = list.map { (it.score / it.maxScore) * 20.0 }.average()
                SubjectAverage(
                    subjectId = subjKey,
                    subjectName = first.subjectName,
                    coefficient = first.coefficient.coerceAtLeast(1L),
                    averageOn20 = avg,
                    evaluationCount = list.size
                )
            }.sortedBy { it.subjectName }

            val totalCoeff = subjectAvgs.sumOf { it.coefficient }.coerceAtLeast(1L)
            val weighted = if (subjectAvgs.isEmpty()) 0.0 else {
                subjectAvgs.sumOf { it.averageOn20 * it.coefficient } / totalCoeff
            }
            Triple(student, subjectAvgs, weighted)
        }.sortedByDescending { it.third }

        val size = rawReports.size
        return rawReports.mapIndexed { idx, (student, subjectAvgs, weighted) ->
            StudentTermReport(
                student = student,
                term = term,
                subjectAverages = subjectAvgs,
                weightedAverage = weighted,
                rank = idx + 1,
                classSize = size,
                mention = mentionForAverage(weighted)
            )
        }
    }

    fun computeAnnualResults(
        school: School,
        students: List<Student>,
        subjects: List<Subject>,
        grades: List<Grade>,
        existingResults: List<AnnualResult>,
        targetClassId: String? = null,
        periodLabel: String = "Résultats annuels ${school.academicYear}",
        staffReaderUids: List<String> = school.memberUids
    ): List<AnnualResult> {
        val category = SchoolCategory.fromRaw(school.category)
        val filteredStudents = students.filter { targetClassId.isNullOrBlank() || it.classId == targetClassId }
        val groupedByClass = filteredStudents.groupBy { it.classId.ifBlank { "non_assignee" } }
        val results = mutableListOf<AnnualResult>()

        groupedByClass.forEach { (_, classStudents) ->
            val computedForClass = classStudents.map { stu ->
                val stuGrades = grades.filter { it.studentId == stu.id }
                val classSubjects = subjects.filter { it.classId == stu.classId || it.classId.isBlank() }

                if (category == SchoolCategory.UNIVERSITE_INSTITUT) {
                    val s1Grades = stuGrades.filter { it.term == TermPeriod.SEMESTRE_1.name || it.term == TermPeriod.TRIMESTRE_1.name }
                    val s2Grades = stuGrades.filter { it.term == TermPeriod.SEMESTRE_2.name || it.term == TermPeriod.TRIMESTRE_2.name || it.term == TermPeriod.TRIMESTRE_3.name }

                    val lines = buildList {
                        val grouped = stuGrades.groupBy { it.subjectId.ifBlank { it.subjectName } }
                        grouped.forEach { (subKey, list) ->
                            val subMeta = classSubjects.firstOrNull { it.id == subKey || it.name == list.first().subjectName }
                            val first = list.first()
                            val avg = round2(list.map { (it.score / it.maxScore) * 20.0 }.average())
                            val credits = subMeta?.credits ?: first.coefficient.coerceAtLeast(1L)
                            val sem = subMeta?.semester ?: if (first.term == TermPeriod.SEMESTRE_2.name) "S2" else "S1"
                            val validated = avg >= school.creditPassThreshold
                            add(
                                ResultSubjectLine(
                                    code = subMeta?.code ?: first.subjectName.take(4).uppercase(Locale.ROOT),
                                    name = first.subjectName,
                                    coefficient = first.coefficient.coerceAtLeast(1L),
                                    credits = credits,
                                    scoreOn20 = avg,
                                    semester = sem,
                                    validated = validated,
                                    appreciation = if (validated) "UE Validée" else "Non validée"
                                )
                            )
                        }
                    }.sortedBy { it.semester + it.name }

                    fun avgForGrades(list: List<Grade>): Double? {
                        if (list.isEmpty()) return null
                        val bySub = list.groupBy { it.subjectId.ifBlank { it.subjectName } }
                        var num = 0.0
                        var den = 0L
                        bySub.values.forEach { subList ->
                            val c = subList.first().coefficient.coerceAtLeast(1L)
                            val a = subList.map { (it.score / it.maxScore) * 20.0 }.average()
                            num += a * c
                            den += c
                        }
                        return if (den > 0L) round2(num / den) else 0.0
                    }

                    val s1Avg = avgForGrades(s1Grades)
                    val s2Avg = avgForGrades(s2Grades)
                    val annualAvg = when {
                        s1Avg != null && s2Avg != null -> round2((s1Avg + s2Avg) / 2.0)
                        s1Avg != null -> s1Avg
                        s2Avg != null -> s2Avg
                        else -> 0.0
                    }
                    val totalUeCredits = lines.sumOf { it.credits }.coerceAtLeast(school.annualCreditsRequired)
                    val directValidatedCredits = lines.filter { it.validated }.sumOf { it.credits }
                    val compensated = school.lmdCompensation && annualAvg >= school.passingAverage && lines.isNotEmpty()
                    val finalCreditsObtained = if (compensated) totalUeCredits else directValidatedCredits
                    val isValide = (finalCreditsObtained >= school.annualCreditsRequired) || (compensated && annualAvg >= school.passingAverage)
                    val decision = if (isValide) ResultDecision.VALIDE_LMD else ResultDecision.AJOURNE_LMD
                    val mention = mentionForAverage(annualAvg)
                    val appreciation = when {
                        isValide && compensated && directValidatedCredits < totalUeCredits ->
                            "Année validée par compensation LMD ($finalCreditsObtained/$totalUeCredits crédits)."
                        isValide ->
                            "Parcours validé ($finalCreditsObtained/$totalUeCredits crédits). Mention $mention."
                        else ->
                            "Ajourné : $finalCreditsObtained/$totalUeCredits crédits obtenus. Rattrapage requis sur les UE non validées."
                    }
                    Triple(stu, annualAvg, Triple(lines, Triple(s1Avg, s2Avg, Pair(finalCreditsObtained, totalUeCredits)), Triple(decision, mention, appreciation)))
                } else {
                    val grouped = stuGrades.groupBy { it.subjectId.ifBlank { it.subjectName } }
                    val lines = grouped.map { (subKey, list) ->
                        val subMeta = classSubjects.firstOrNull { it.id == subKey || it.name == list.first().subjectName }
                        val first = list.first()
                        val avg = round2(list.map { (it.score / it.maxScore) * 20.0 }.average())
                        val passed = avg >= school.passingAverage
                        ResultSubjectLine(
                            code = subMeta?.code ?: first.subjectName.take(4).uppercase(Locale.ROOT),
                            name = first.subjectName,
                            coefficient = first.coefficient.coerceAtLeast(1L),
                            credits = 0L,
                            scoreOn20 = avg,
                            semester = "",
                            validated = passed,
                            appreciation = mentionForAverage(avg)
                        )
                    }.sortedBy { it.name }

                    val totalCoeff = lines.sumOf { it.coefficient }.coerceAtLeast(1L)
                    val annualAvg = if (lines.isEmpty()) 0.0 else round2(lines.sumOf { it.scoreOn20 * it.coefficient } / totalCoeff)
                    val decision = when {
                        annualAvg >= school.passingAverage -> ResultDecision.ADMIS
                        annualAvg >= (school.passingAverage - 2.0).coerceAtLeast(0.0) -> ResultDecision.AJOURNE
                        else -> ResultDecision.REDOUBLEMENT
                    }
                    val mention = mentionForAverage(annualAvg)
                    val appreciation = when (decision) {
                        ResultDecision.ADMIS -> if (category == SchoolCategory.PRIMAIRE_MATERNELLE) {
                            "Passage en classe supérieure avec la mention $mention."
                        } else {
                            "Admis(e) en classe supérieure. Mention $mention."
                        }
                        ResultDecision.AJOURNE -> "Résultat insuffisant pour le passage direct ; examen de passage / session de rattrapage conseillé."
                        else -> "Moyenne inférieure au seuil requis (${school.passingAverage}/20). Redoublement décidé par le conseil."
                    }
                    Triple(stu, annualAvg, Triple(lines, Triple(null as Double?, null as Double?, Pair(0L, 0L)), Triple(decision, mention, appreciation)))
                }
            }.sortedByDescending { it.second }

            val size = computedForClass.size.toLong()
            computedForClass.forEachIndexed { idx, (stu, annualAvg, bundle) ->
                val (lines, lmdTuple, outcomeTuple) = bundle
                val (s1Avg, s2Avg, creditsPair) = lmdTuple
                val (decision, mention, appreciation) = outcomeTuple
                val safeYear = school.academicYear.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                val deterministicId = "res_${stu.id}_$safeYear"
                val existing = existingResults.firstOrNull { it.id == deterministicId || (it.studentId == stu.id && it.academicYear == school.academicYear) }
                val verHash = stu.accessCodeHash?.takeIf { it.isNotBlank() }
                    ?: existing?.verificationHash?.takeIf { it.isNotBlank() }
                    ?: computeStudentVerificationHash(school.id, stu.matricule, stu.birthDate, stu.accessCodeDisplay ?: "000000")

                results.add(
                    AnnualResult(
                        id = existing?.id ?: deterministicId,
                        schoolId = school.id,
                        schoolName = school.name,
                        category = category.name,
                        studentId = stu.id,
                        matricule = stu.matricule,
                        studentFullName = stu.fullName,
                        birthDate = stu.birthDate,
                        classId = stu.classId,
                        className = stu.className,
                        level = stu.level,
                        faculty = stu.faculty,
                        department = stu.department,
                        program = stu.program,
                        academicYear = school.academicYear,
                        periodLabel = periodLabel,
                        average = annualAvg,
                        semester1Average = s1Avg,
                        semester2Average = s2Avg,
                        annualAverage = annualAvg,
                        rank = (idx + 1).toLong(),
                        classSize = size,
                        creditsObtained = creditsPair.first,
                        creditsRequired = creditsPair.second,
                        decision = decision.name,
                        mention = mention,
                        appreciation = appreciation,
                        subjectLinesJson = serializeSubjectLines(lines),
                        status = existing?.status ?: ResultStatus.BROUILLON.name,
                        scheduledPublishAtMillis = existing?.scheduledPublishAtMillis ?: 0L,
                        publishedAtMillis = existing?.publishedAtMillis ?: 0L,
                        publishedByUid = existing?.publishedByUid,
                        publishedByName = existing?.publishedByName,
                        revisionCount = existing?.revisionCount ?: 0L,
                        lastModifiedReason = existing?.lastModifiedReason,
                        verificationHash = verHash,
                        readerUids = staffReaderUids.distinct().take(60),
                        createdAt = existing?.createdAt
                    )
                )
            }
        }
        return results
    }

    fun computeStudentBalances(
        students: List<Student>,
        payments: List<Payment>
    ): List<StudentBalance> {
        val paidByStudent = payments
            .filter { it.providerStatus == ProviderStatus.CONFIRME.name }
            .groupBy { it.studentId }
            .mapValues { (_, list) -> list.sumOf { it.amountFcfa } }

        return students.map { student ->
            val paid = paidByStudent[student.id] ?: 0L
            val remaining = (student.tuitionTotalFcfa - paid).coerceAtLeast(0L)
            StudentBalance(
                student = student,
                expectedFcfa = student.tuitionTotalFcfa,
                paidFcfa = paid,
                remainingFcfa = remaining
            )
        }.sortedByDescending { it.remainingFcfa }
    }
}
