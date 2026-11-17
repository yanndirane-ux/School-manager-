package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.model.*
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

object PdfAndCsvExporter {

    private fun docsDir(context: Context): File {
        val dir = File(context.cacheDir, "documents")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun openOrShareFile(context: Context, file: File, mimeType: String, title: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(viewIntent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(chooser)
        } catch (_: Exception) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    private fun createStyledDocument(
        context: Context,
        fileName: String,
        school: School,
        docTitle: String,
        reference: String,
        drawBody: (android.graphics.Canvas, Paint, Paint, Paint, Float) -> Unit
    ): File {
        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdf.startPage(pageInfo)
        val canvas = page.canvas

        val headerBg = Paint().apply { color = Color.rgb(15, 30, 61) }
        val accentPaint = Paint().apply { color = Color.rgb(37, 99, 235) }
        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 18f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val subTitlePaint = Paint().apply {
            color = Color.rgb(191, 219, 254)
            textSize = 11f
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 11.5f
            isAntiAlias = true
        }
        val boldPaint = Paint().apply {
            color = Color.rgb(15, 30, 61)
            textSize = 12f
            isFakeBoldText = true
            isAntiAlias = true
        }

        // En-tête bleu nuit
        canvas.drawRect(0f, 0f, 595f, 96f, headerBg)
        canvas.drawRect(0f, 96f, 595f, 102f, accentPaint)
        canvas.drawText(school.name.uppercase(Locale.FRENCH), 32f, 36f, titlePaint)
        canvas.drawText("${school.city}, ${school.country}  •  Année scolaire ${school.academicYear}", 32f, 56f, subTitlePaint)
        canvas.drawText(school.slogan, 32f, 76f, subTitlePaint)

        // Titre du document + référence
        val docTitlePaint = Paint().apply {
            color = Color.rgb(15, 30, 61)
            textSize = 16f
            isFakeBoldText = true
            isAntiAlias = true
        }
        canvas.drawText(docTitle, 32f, 134f, docTitlePaint)
        bodyPaint.color = Color.rgb(100, 116, 139)
        canvas.drawText("Réf : $reference   •   Édité le ${Formatters.todayIso()}", 32f, 152f, bodyPaint)
        bodyPaint.color = Color.rgb(15, 23, 42)

        drawBody(canvas, bodyPaint, boldPaint, accentPaint, 178f)

        // Pied de page
        val footerPaint = Paint().apply {
            color = Color.rgb(100, 116, 139)
            textSize = 9.5f
            isAntiAlias = true
        }
        canvas.drawText("Document généré par SchoolManager — « Votre établissement, mieux organisé. »", 32f, 816f, footerPaint)

        pdf.finishPage(page)
        val outFile = File(docsDir(context), fileName)
        FileOutputStream(outFile).use { pdf.writeTo(it) }
        pdf.close()
        return outFile
    }

    fun generateReportCardPdf(
        context: Context,
        school: School,
        report: StudentTermReport,
        reference: String
    ): File {
        val safeMat = report.student.matricule.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return createStyledDocument(
            context = context,
            fileName = "Bulletin_${safeMat}_${report.term.name}.pdf",
            school = school,
            docTitle = "BULLETIN SCOLAIRE — ${report.term.label.uppercase(Locale.FRENCH)}",
            reference = reference
        ) { canvas, body, bold, accent, startY ->
            var y = startY
            canvas.drawText("Élève : ${report.student.fullName}", 32f, y, bold)
            canvas.drawText("Matricule : ${report.student.matricule}", 360f, y, body)
            y += 20f
            canvas.drawText("Classe : ${report.student.className} (${report.student.level})", 32f, y, body)
            canvas.drawText("Tuteur : ${report.student.guardianName}", 360f, y, body)
            y += 28f

            // En-tête tableau
            val headerRowPaint = Paint().apply { color = Color.rgb(232, 241, 255) }
            canvas.drawRect(32f, y - 14f, 563f, y + 10f, headerRowPaint)
            canvas.drawText("Matière", 40f, y + 2f, bold)
            canvas.drawText("Coeff", 300f, y + 2f, bold)
            canvas.drawText("Moy / 20", 365f, y + 2f, bold)
            canvas.drawText("Points", 445f, y + 2f, bold)
            canvas.drawText("Appréciation", 500f, y + 2f, bold)
            y += 24f

            if (report.subjectAverages.isEmpty()) {
                canvas.drawText("Aucune note enregistrée pour cette période.", 40f, y, body)
                y += 24f
            } else {
                report.subjectAverages.take(18).forEach { subj ->
                    val points = subj.averageOn20 * subj.coefficient
                    canvas.drawText(subj.subjectName.take(32), 40f, y, body)
                    canvas.drawText("${subj.coefficient}", 308f, y, body)
                    canvas.drawText(String.format(Locale.FRENCH, "%.2f", subj.averageOn20), 370f, y, body)
                    canvas.drawText(String.format(Locale.FRENCH, "%.2f", points), 448f, y, body)
                    canvas.drawText(Formatters.mentionForAverage(subj.averageOn20), 500f, y, body)
                    y += 20f
                }
            }

            y += 16f
            canvas.drawLine(32f, y, 563f, y, accent)
            y += 24f
            canvas.drawText(
                "Moyenne Générale Pondérée : ${String.format(Locale.FRENCH, "%.2f", report.weightedAverage)} / 20",
                32f, y, bold
            )
            canvas.drawText("Rang : ${report.rank}e / ${report.classSize}", 380f, y, bold)
            y += 20f
            canvas.drawText("Mention du Conseil de Classe : ${report.mention}", 32f, y, bold)
            y += 42f
            canvas.drawText("Visa de la Direction :", 390f, y, body)
        }
    }

    fun generatePaymentReceiptPdf(
        context: Context,
        school: School,
        payment: Payment,
        balance: StudentBalance?
    ): File {
        val safeRec = payment.receiptNumber.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return createStyledDocument(
            context = context,
            fileName = "Recu_$safeRec.pdf",
            school = school,
            docTitle = "REÇU DE PAIEMENT DE SCOLARITÉ",
            reference = payment.receiptNumber
        ) { canvas, body, bold, accent, startY ->
            var y = startY
            canvas.drawText("Élève : ${payment.studentName}", 32f, y, bold)
            canvas.drawText("Matricule : ${payment.studentMatricule}", 360f, y, body)
            y += 22f
            canvas.drawText("Classe : ${payment.className}", 32f, y, body)
            canvas.drawText("Tranche : ${PaymentTranche.fromRaw(payment.tranche).label}", 360f, y, body)
            y += 28f

            val boxPaint = Paint().apply { color = Color.rgb(232, 241, 255) }
            canvas.drawRect(32f, y, 563f, y + 72f, boxPaint)
            canvas.drawText("Montant encaissé : ${Formatters.fcfa(payment.amountFcfa)}", 48f, y + 28f, bold)
            canvas.drawText(
                "Mode : ${PaymentMethod.fromRaw(payment.method).label}  •  Statut : ${ProviderStatus.fromRaw(payment.providerStatus).label}",
                48f, y + 50f, body
            )
            y += 96f

            canvas.drawText("Référence transaction : ${payment.transactionRef}", 32f, y, body)
            y += 20f
            if (balance != null) {
                canvas.drawText("Scolarité totale : ${Formatters.fcfa(balance.expectedFcfa)}", 32f, y, body)
                y += 18f
                canvas.drawText("Total payé à ce jour : ${Formatters.fcfa(balance.paidFcfa)}", 32f, y, body)
                y += 18f
                canvas.drawText("Reste à payer : ${Formatters.fcfa(balance.remainingFcfa)}", 32f, y, bold)
                y += 24f
            }
            canvas.drawLine(32f, y, 563f, y, accent)
            y += 24f
            canvas.drawText("Caissier / Comptable : ${payment.recordedByName}", 32f, y, body)
            canvas.drawText("Signature & Cachet :", 400f, y, body)
        }
    }

    fun generateOfficialCertificatePdf(
        context: Context,
        school: School,
        student: Student,
        docType: DocumentType,
        reference: String,
        directorName: String
    ): File {
        val safeMat = student.matricule.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return createStyledDocument(
            context = context,
            fileName = "${docType.name}_$safeMat.pdf",
            school = school,
            docTitle = docType.label.uppercase(Locale.FRENCH),
            reference = reference
        ) { canvas, body, bold, _, startY ->
            var y = startY + 12f
            canvas.drawText("Le Directeur de l'établissement ${school.name}, sis à ${school.city} (${school.country}),", 32f, y, body)
            y += 22f
            canvas.drawText("certifie par la présente que l'élève :", 32f, y, body)
            y += 32f

            canvas.drawText("Nom et Prénoms : ${student.fullName}", 48f, y, bold)
            y += 22f
            canvas.drawText("Né(e) le : ${student.birthDate}  à  ${student.birthPlace ?: school.city}", 48f, y, body)
            y += 22f
            canvas.drawText("Matricule : ${student.matricule}", 48f, y, body)
            y += 22f
            canvas.drawText("Classe / Niveau : ${student.className} (${student.level}) — Campus : ${student.campus}", 48f, y, body)
            y += 22f
            canvas.drawText("Année scolaire : ${student.academicYear}", 48f, y, body)
            y += 34f

            val statement = when (docType) {
                DocumentType.CERTIFICAT_SCOLARITE ->
                    "est régulièrement inscrit(e) dans notre établissement au titre de l'année scolaire ${student.academicYear}."
                DocumentType.ATTESTATION_PRESENCE ->
                    "fréquente assidûment les cours dispensés dans sa classe au titre de l'année scolaire ${student.academicYear}."
                else ->
                    "fait partie des effectifs officiels de notre établissement pour l'année scolaire ${student.academicYear}."
            }
            canvas.drawText(statement, 32f, y, body)
            y += 26f
            canvas.drawText("En foi de quoi, le présent document lui est délivré pour servir et valoir ce que de droit.", 32f, y, body)
            y += 56f
            canvas.drawText("Fait à ${school.city}, le ${Formatters.todayIso()}", 360f, y, body)
            y += 24f
            canvas.drawText("La Direction ($directorName)", 360f, y, bold)
        }
    }

    fun exportStudentsPdf(context: Context, school: School, students: List<Student>): File {
        return createStyledDocument(
            context = context,
            fileName = "Liste_Etudiants_${Formatters.todayIso()}.pdf",
            school = school,
            docTitle = "REGISTRE OFFICIEL DES ÉTUDIANTS (${students.size})",
            reference = "EXP-${Formatters.todayIso()}"
        ) { canvas, body, bold, _, startY ->
            var y = startY
            canvas.drawText("Matricule", 32f, y, bold)
            canvas.drawText("Nom & Prénoms", 135f, y, bold)
            canvas.drawText("Classe", 340f, y, bold)
            canvas.drawText("Tuteur / Tél.", 420f, y, bold)
            y += 18f
            students.take(28).forEach { st ->
                canvas.drawText(st.matricule.take(14), 32f, y, body)
                canvas.drawText(st.fullName.take(28), 135f, y, body)
                canvas.drawText(st.className.take(11), 340f, y, body)
                canvas.drawText(st.guardianPhone.take(18), 420f, y, body)
                y += 18f
            }
        }
    }

    fun exportStudentsCsv(context: Context, students: List<Student>): File {
        val file = File(docsDir(context), "Etudiants_Export_${Formatters.todayIso()}.csv")
        val sb = StringBuilder()
        sb.append('\uFEFF') // BOM UTF-8 pour Excel
        sb.appendLine("Matricule;Nom;Prenom;Sexe;DateNaissance;Classe;Niveau;Campus;Statut;Tuteur;TelephoneTuteur;ScolariteFCFA")
        students.forEach { s ->
            sb.appendLine(
                listOf(
                    s.matricule, s.lastName, s.firstName, s.gender, s.birthDate,
                    s.className, s.level, s.campus, s.status, s.guardianName,
                    s.guardianPhone, s.tuitionTotalFcfa.toString()
                ).joinToString(";") { it.replace(";", ",") }
            )
        }
        file.writeText(sb.toString(), Charsets.UTF_8)
        return file
    }

    fun parseStudentsFromCsvUri(
        context: Context,
        uri: Uri,
        defaultClass: SchoolClass?,
        school: School,
        startIndex: Int
    ): List<Student> {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: return emptyList()
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val dataLines = if (lines.first().contains("Nom", ignoreCase = true)) lines.drop(1) else lines
        val results = mutableListOf<Student>()
        dataLines.forEachIndexed { idx, line ->
            val sep = if (line.contains(';')) ';' else ','
            val cols = line.split(sep).map { it.trim() }
            if (cols.size >= 2) {
                val lastName = cols.getOrNull(0).orEmpty().ifBlank { "Élève" }
                val firstName = cols.getOrNull(1).orEmpty().ifBlank { "Nouveau" }
                val gender = cols.getOrNull(2)?.uppercase(Locale.ROOT)?.take(1)?.let { if (it == "F") "F" else "M" } ?: "M"
                val birthDate = cols.getOrNull(3).orEmpty().ifBlank { "2012-01-01" }
                val guardianName = cols.getOrNull(4).orEmpty().ifBlank { "Parent de $firstName" }
                val guardianPhone = cols.getOrNull(5).orEmpty().ifBlank { "+237600000000" }
                results.add(
                    Student(
                        schoolId = school.id,
                        matricule = Formatters.generateMatricule(school.name, startIndex + idx),
                        firstName = firstName,
                        lastName = lastName,
                        gender = gender,
                        birthDate = birthDate,
                        classId = defaultClass?.id.orEmpty(),
                        className = defaultClass?.name ?: "Non assignée",
                        level = defaultClass?.level ?: "Général",
                        faculty = defaultClass?.faculty,
                        department = defaultClass?.department,
                        program = defaultClass?.program,
                        academicYear = school.academicYear,
                        campus = defaultClass?.campus ?: school.campuses.firstOrNull() ?: "Campus Principal",
                        status = StudentStatus.INSCRIT.name,
                        guardianName = guardianName,
                        guardianPhone = guardianPhone,
                        tuitionTotalFcfa = (defaultClass?.tuitionFeeFcfa ?: 150_000L) + (defaultClass?.registrationFeeFcfa ?: 25_000L),
                        historyNotes = listOf("Importé par CSV le ${Formatters.todayIso()}")
                    )
                )
            }
        }
        return results
    }

    fun parseGradesFromCsvUri(
        context: Context,
        uri: Uri,
        students: List<Student>,
        subjects: List<Subject>,
        defaultTerm: TermPeriod,
        teacherName: String
    ): List<Grade> {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: return emptyList()
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val header = lines.first()
        val dataLines = if (
            header.contains("Matricule", ignoreCase = true) ||
            header.contains("Note", ignoreCase = true)
        ) lines.drop(1) else lines

        val parsed = mutableListOf<Grade>()
        dataLines.forEach { line ->
            val sep = if (line.contains(';')) ';' else ','
            val cols = line.split(sep).map { it.trim() }
            if (cols.size >= 3) {
                val mat = cols[0]
                val stu = students.firstOrNull {
                    it.matricule.equals(mat, ignoreCase = true) ||
                        it.fullName.equals(mat, ignoreCase = true)
                } ?: return@forEach

                val subjQuery = cols[1]
                val subjMeta = subjects.firstOrNull {
                    (it.classId == stu.classId || it.classId.isBlank()) &&
                        (it.code.equals(subjQuery, ignoreCase = true) || it.name.equals(subjQuery, ignoreCase = true))
                } ?: subjects.firstOrNull {
                    it.code.equals(subjQuery, ignoreCase = true) || it.name.equals(subjQuery, ignoreCase = true)
                }

                val score = cols[2].replace(",", ".").toDoubleOrNull()?.coerceIn(0.0, 20.0) ?: return@forEach
                val coeff = cols.getOrNull(3)?.toLongOrNull()?.coerceIn(1L, 20L) ?: subjMeta?.coefficient ?: 1L
                val rawTerm = cols.getOrNull(4).orEmpty().uppercase(Locale.ROOT)
                val term = when {
                    rawTerm.contains("S1") || rawTerm == "SEMESTRE_1" -> TermPeriod.SEMESTRE_1
                    rawTerm.contains("S2") || rawTerm == "SEMESTRE_2" -> TermPeriod.SEMESTRE_2
                    rawTerm.contains("T1") || rawTerm == "TRIMESTRE_1" -> TermPeriod.TRIMESTRE_1
                    rawTerm.contains("T2") || rawTerm == "TRIMESTRE_2" -> TermPeriod.TRIMESTRE_2
                    rawTerm.contains("T3") || rawTerm == "TRIMESTRE_3" -> TermPeriod.TRIMESTRE_3
                    else -> defaultTerm
                }
                val rawEval = cols.getOrNull(5).orEmpty().uppercase(Locale.ROOT)
                val evalType = when {
                    rawEval.contains("COMP") || rawEval.contains("EXAM") -> EvaluationType.COMPOSITION
                    rawEval.contains("INTER") -> EvaluationType.INTERROGATION
                    else -> EvaluationType.DEVOIR
                }

                parsed.add(
                    Grade(
                        schoolId = stu.schoolId,
                        studentId = stu.id,
                        studentName = stu.fullName,
                        classId = stu.classId,
                        className = stu.className,
                        subjectId = subjMeta?.id ?: subjQuery.lowercase(Locale.ROOT).replace(" ", "_"),
                        subjectName = subjMeta?.name ?: subjQuery,
                        coefficient = coeff,
                        term = term.name,
                        evaluationType = evalType.name,
                        score = score,
                        maxScore = 20.0,
                        teacherName = teacherName.ifBlank { "Import CSV" },
                        comment = "Importé depuis fichier Excel/CSV"
                    )
                )
            }
        }
        return parsed
    }

    fun generateAnnualBulletinPdf(
        context: Context,
        school: School,
        result: AnnualResult,
        reference: String
    ): File {
        val safeMat = result.matricule.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val lines = Formatters.parseSubjectLines(result.subjectLinesJson)
        val decisionLabel = ResultDecision.fromRaw(result.decision).label
        return createStyledDocument(
            context = context,
            fileName = "Bulletin_Annuel_$safeMat.pdf",
            school = school,
            docTitle = "BULLETIN ANNUEL DE RÉSULTATS — ${result.academicYear}",
            reference = reference
        ) { canvas, body, bold, accent, startY ->
            var y = startY
            canvas.drawText("Élève : ${result.studentFullName}", 32f, y, bold)
            canvas.drawText("Matricule : ${result.matricule}", 360f, y, bold)
            y += 18f
            canvas.drawText("Classe : ${result.className} (${result.level})", 32f, y, body)
            canvas.drawText("Catégorie : ${SchoolCategory.fromRaw(result.category).shortLabel}", 360f, y, body)
            y += 18f
            canvas.drawText("Période : ${result.periodLabel}", 32f, y, body)
            canvas.drawText("Né(e) le : ${result.birthDate}", 360f, y, body)
            y += 26f

            val headerRowPaint = Paint().apply { color = Color.rgb(232, 241, 255) }
            canvas.drawRect(32f, y - 14f, 563f, y + 10f, headerRowPaint)
            canvas.drawText("Matière", 40f, y + 2f, bold)
            canvas.drawText("Coeff", 290f, y + 2f, bold)
            canvas.drawText("Moy / 20", 355f, y + 2f, bold)
            canvas.drawText("Points", 435f, y + 2f, bold)
            canvas.drawText("Appréciation", 495f, y + 2f, bold)
            y += 22f

            if (lines.isEmpty()) {
                canvas.drawText("Aucune matière détaillée disponible.", 40f, y, body)
                y += 22f
            } else {
                lines.take(19).forEach { line ->
                    val points = line.scoreOn20 * line.coefficient
                    canvas.drawText(line.name.take(30), 40f, y, body)
                    canvas.drawText("${line.coefficient}", 298f, y, body)
                    canvas.drawText(String.format(Locale.FRENCH, "%.2f", line.scoreOn20), 362f, y, body)
                    canvas.drawText(String.format(Locale.FRENCH, "%.2f", points), 438f, y, body)
                    canvas.drawText(line.appreciation.take(14), 495f, y, body)
                    y += 18f
                }
            }

            y += 10f
            canvas.drawLine(32f, y, 563f, y, accent)
            y += 22f
            canvas.drawText(
                "Moyenne Annuelle : ${String.format(Locale.FRENCH, "%.2f", result.annualAverage)} / 20",
                32f, y, bold
            )
            canvas.drawText("Rang : ${result.rank}e / ${result.classSize}", 380f, y, bold)
            y += 20f
            canvas.drawText("Mention : ${result.mention}", 32f, y, bold)
            canvas.drawText("Décision : $decisionLabel", 260f, y, bold)
            y += 22f
            canvas.drawText("Appréciation : ${result.appreciation.take(86)}", 32f, y, body)
            if (result.revisionCount > 0L) {
                y += 18f
                canvas.drawText("Version corrigée n°${result.revisionCount} — ${result.lastModifiedReason.orEmpty().take(70)}", 32f, y, body)
            }
            y += 34f
            canvas.drawText("Visa de la Direction :", 390f, y, bold)
        }
    }

    fun generateUniversityTranscriptPdf(
        context: Context,
        school: School,
        result: AnnualResult,
        reference: String
    ): File {
        val safeMat = result.matricule.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val lines = Formatters.parseSubjectLines(result.subjectLinesJson)
        val decisionLabel = ResultDecision.fromRaw(result.decision).label
        return createStyledDocument(
            context = context,
            fileName = "Releve_Universitaire_$safeMat.pdf",
            school = school,
            docTitle = "RELEVÉ DE NOTES UNIVERSITAIRE (LMD) — ${result.academicYear}",
            reference = reference
        ) { canvas, body, bold, accent, startY ->
            var y = startY
            canvas.drawText("Étudiant(e) : ${result.studentFullName}", 32f, y, bold)
            canvas.drawText("Matricule : ${result.matricule}", 360f, y, bold)
            y += 18f
            canvas.drawText("Faculté / École : ${result.faculty ?: "Non précisée"}", 32f, y, body)
            canvas.drawText("Département : ${result.department ?: "Général"}", 360f, y, body)
            y += 18f
            canvas.drawText("Filière : ${result.program ?: result.className}", 32f, y, body)
            canvas.drawText("Niveau LMD : ${result.level}", 360f, y, bold)
            y += 26f

            val headerRowPaint = Paint().apply { color = Color.rgb(232, 241, 255) }
            canvas.drawRect(32f, y - 14f, 563f, y + 10f, headerRowPaint)
            canvas.drawText("Code", 36f, y + 2f, bold)
            canvas.drawText("Unité d'Enseignement (UE)", 95f, y + 2f, bold)
            canvas.drawText("Sem.", 315f, y + 2f, bold)
            canvas.drawText("Créd.", 365f, y + 2f, bold)
            canvas.drawText("Note /20", 418f, y + 2f, bold)
            canvas.drawText("Validation", 490f, y + 2f, bold)
            y += 22f

            if (lines.isEmpty()) {
                canvas.drawText("Aucune UE enregistrée.", 40f, y, body)
                y += 22f
            } else {
                lines.take(18).forEach { ue ->
                    canvas.drawText(ue.code.take(8), 36f, y, body)
                    canvas.drawText(ue.name.take(28), 95f, y, body)
                    canvas.drawText(ue.semester.ifBlank { "S1" }.take(5), 318f, y, body)
                    canvas.drawText("${ue.credits}", 372f, y, body)
                    canvas.drawText(String.format(Locale.FRENCH, "%.2f", ue.scoreOn20), 424f, y, body)
                    canvas.drawText(if (ue.validated) "Validée" else "Non val.", 490f, y, body)
                    y += 18f
                }
            }

            y += 12f
            canvas.drawLine(32f, y, 563f, y, accent)
            y += 22f
            val s1 = result.semester1Average?.let { String.format(Locale.FRENCH, "%.2f/20", it) } ?: "—"
            val s2 = result.semester2Average?.let { String.format(Locale.FRENCH, "%.2f/20", it) } ?: "—"
            canvas.drawText("Moyenne Semestre 1 : $s1", 32f, y, body)
            canvas.drawText("Moyenne Semestre 2 : $s2", 290f, y, body)
            y += 20f
            canvas.drawText(
                "Moyenne Annuelle : ${String.format(Locale.FRENCH, "%.2f", result.annualAverage)} / 20",
                32f, y, bold
            )
            canvas.drawText("Crédits obtenus : ${result.creditsObtained} / ${result.creditsRequired}", 290f, y, bold)
            y += 20f
            canvas.drawText("Mention : ${result.mention}   •   Décision : $decisionLabel", 32f, y, bold)
            y += 20f
            canvas.drawText(result.appreciation.take(86), 32f, y, body)
            y += 34f
            canvas.drawText("Le Doyen / Directeur Académique :", 350f, y, bold)
        }
    }

    fun generateResultAttestationPdf(
        context: Context,
        school: School,
        result: AnnualResult,
        reference: String,
        directorName: String
    ): File {
        val safeMat = result.matricule.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val decisionLabel = ResultDecision.fromRaw(result.decision).label
        return createStyledDocument(
            context = context,
            fileName = "Attestation_Resultats_$safeMat.pdf",
            school = school,
            docTitle = "ATTESTATION OFFICIELLE DE RÉSULTATS",
            reference = reference
        ) { canvas, body, bold, accent, startY ->
            var y = startY + 12f
            canvas.drawText("La Direction de l'établissement ${school.name}, sis à ${school.city} (${school.country}),", 32f, y, body)
            y += 22f
            canvas.drawText("atteste que l'apprenant(e) désigné(e) ci-dessous a obtenu les résultats officiels suivants :", 32f, y, body)
            y += 32f

            canvas.drawText("Nom et Prénoms : ${result.studentFullName}", 48f, y, bold)
            y += 22f
            canvas.drawText("Matricule : ${result.matricule}   •   Né(e) le : ${result.birthDate}", 48f, y, body)
            y += 22f
            canvas.drawText("Classe / Filière : ${result.program?.let { "$it — " } ?: ""}${result.className} (${result.level})", 48f, y, body)
            y += 22f
            canvas.drawText("Année académique : ${result.academicYear}   •   ${result.periodLabel}", 48f, y, body)
            y += 30f

            val boxPaint = Paint().apply { color = Color.rgb(232, 241, 255) }
            canvas.drawRect(32f, y, 563f, y + 86f, boxPaint)
            canvas.drawText(
                "Moyenne Générale : ${String.format(Locale.FRENCH, "%.2f", result.annualAverage)} / 20   •   Mention : ${result.mention}",
                48f, y + 28f, bold
            )
            val extra = if (result.category == SchoolCategory.UNIVERSITE_INSTITUT.name) {
                "Crédits validés : ${result.creditsObtained} / ${result.creditsRequired}"
            } else {
                "Classement : ${result.rank}e / ${result.classSize}"
            }
            canvas.drawText(extra, 48f, y + 50f, body)
            canvas.drawText("Décision finale : $decisionLabel", 48f, y + 72f, bold)
            y += 116f

            canvas.drawLine(32f, y, 563f, y, accent)
            y += 26f
            canvas.drawText("En foi de quoi, la présente attestation lui est délivrée pour servir et valoir ce que de droit.", 32f, y, body)
            y += 50f
            canvas.drawText("Fait à ${school.city}, le ${Formatters.todayIso()}", 350f, y, body)
            y += 24f
            canvas.drawText("La Direction ($directorName)", 350f, y, bold)
        }
    }

    fun generateDecisionCertificatePdf(
        context: Context,
        school: School,
        result: AnnualResult,
        reference: String,
        directorName: String
    ): File {
        val safeMat = result.matricule.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val dec = ResultDecision.fromRaw(result.decision)
        return createStyledDocument(
            context = context,
            fileName = "Decision_Passage_$safeMat.pdf",
            school = school,
            docTitle = "DÉCISION DE PASSAGE / VALIDATION ACADÉMIQUE",
            reference = reference
        ) { canvas, body, bold, _, startY ->
            var y = startY + 12f
            canvas.drawText("Vu les délibérations du jury de fin d'année académique ${result.academicYear},", 32f, y, body)
            y += 22f
            canvas.drawText("Vu les résultats enregistrés sous le matricule ${result.matricule},", 32f, y, body)
            y += 32f

            canvas.drawText("Apprenant(e) : ${result.studentFullName}", 48f, y, bold)
            y += 22f
            canvas.drawText("Catégorie : ${SchoolCategory.fromRaw(result.category).label}", 48f, y, body)
            y += 22f
            canvas.drawText("Niveau / Classe : ${result.className} (${result.level})", 48f, y, body)
            if (!result.program.isNullOrBlank()) {
                y += 22f
                canvas.drawText("Filière : ${result.program}", 48f, y, body)
            }
            y += 34f

            val boxBg = when (dec) {
                ResultDecision.ADMIS, ResultDecision.VALIDE_LMD -> Color.rgb(220, 252, 231)
                ResultDecision.AJOURNE, ResultDecision.AJOURNE_LMD -> Color.rgb(254, 243, 199)
                ResultDecision.REDOUBLEMENT -> Color.rgb(254, 226, 226)
            }
            val boxPaint = Paint().apply { color = boxBg }
            canvas.drawRect(32f, y, 563f, y + 76f, boxPaint)
            canvas.drawText("DÉCISION OFFICIELLE : ${dec.label.uppercase(Locale.FRENCH)}", 48f, y + 30f, bold)
            canvas.drawText(
                "Moyenne : ${String.format(Locale.FRENCH, "%.2f", result.annualAverage)}/20 • Mention : ${result.mention}",
                48f, y + 54f, body
            )
            y += 104f
            canvas.drawText("Observation du conseil : ${result.appreciation.take(86)}", 32f, y, body)
            y += 56f
            canvas.drawText("Fait à ${school.city}, le ${Formatters.todayIso()}", 350f, y, body)
            y += 24f
            canvas.drawText("Signature & Cachet ($directorName)", 350f, y, bold)
        }
    }

    fun exportResultsStatisticsPdf(
        context: Context,
        school: School,
        results: List<AnnualResult>
    ): File {
        val total = results.size
        val admis = results.count { it.decision == ResultDecision.ADMIS.name || it.decision == ResultDecision.VALIDE_LMD.name }
        val ajournes = results.count { it.decision == ResultDecision.AJOURNE.name || it.decision == ResultDecision.AJOURNE_LMD.name }
        val redoublants = results.count { it.decision == ResultDecision.REDOUBLEMENT.name }
        val rate = if (total > 0) (admis * 100.0 / total) else 0.0
        val avgAll = if (total > 0) results.map { it.annualAverage }.average() else 0.0

        return createStyledDocument(
            context = context,
            fileName = "Statistiques_Resultats_${Formatters.todayIso()}.pdf",
            school = school,
            docTitle = "RAPPORT STATISTIQUE DES RÉSULTATS (${school.academicYear})",
            reference = "STAT-${Formatters.todayIso()}"
        ) { canvas, body, bold, accent, startY ->
            var y = startY
            val boxPaint = Paint().apply { color = Color.rgb(232, 241, 255) }
            canvas.drawRect(32f, y, 563f, y + 74f, boxPaint)
            canvas.drawText(
                "Effectif évalué : $total   •   Taux de réussite : ${String.format(Locale.FRENCH, "%.1f", rate)}%   •   Moyenne : ${String.format(Locale.FRENCH, "%.2f", avgAll)}/20",
                44f, y + 28f, bold
            )
            canvas.drawText(
                "Admis / Validés : $admis     •     Ajournés : $ajournes     •     Redoublants : $redoublants",
                44f, y + 54f, body
            )
            y += 100f

            canvas.drawText("Matricule", 32f, y, bold)
            canvas.drawText("Apprenant", 135f, y, bold)
            canvas.drawText("Classe", 315f, y, bold)
            canvas.drawText("Moy.", 405f, y, bold)
            canvas.drawText("Décision", 460f, y, bold)
            y += 10f
            canvas.drawLine(32f, y, 563f, y, accent)
            y += 18f

            results.sortedByDescending { it.annualAverage }.take(24).forEach { r ->
                canvas.drawText(r.matricule.take(15), 32f, y, body)
                canvas.drawText(r.studentFullName.take(24), 135f, y, body)
                canvas.drawText(r.className.take(12), 315f, y, body)
                canvas.drawText(String.format(Locale.FRENCH, "%.2f", r.annualAverage), 405f, y, body)
                canvas.drawText(ResultDecision.fromRaw(r.decision).label.take(16), 460f, y, body)
                y += 18f
            }
        }
    }

    fun exportResultsStatisticsCsv(context: Context, results: List<AnnualResult>): File {
        val file = File(docsDir(context), "Statistiques_Resultats_${Formatters.todayIso()}.csv")
        val sb = StringBuilder()
        sb.append('\uFEFF')
        sb.appendLine("Matricule;NomComplet;Classe;Filiere;Niveau;MoyenneAnnuelle;CreditsObtenus;CreditsRequis;Rang;Mention;Decision;StatutPublication")
        results.sortedByDescending { it.annualAverage }.forEach { r ->
            sb.appendLine(
                listOf(
                    r.matricule,
                    r.studentFullName,
                    r.className,
                    r.program.orEmpty(),
                    r.level,
                    String.format(Locale.US, "%.2f", r.annualAverage),
                    r.creditsObtained.toString(),
                    r.creditsRequired.toString(),
                    "${r.rank}/${r.classSize}",
                    r.mention,
                    ResultDecision.fromRaw(r.decision).label,
                    ResultStatus.fromRaw(r.status).label
                ).joinToString(";") { it.replace(";", ",") }
            )
        }
        file.writeText(sb.toString(), Charsets.UTF_8)
        return file
    }
}

