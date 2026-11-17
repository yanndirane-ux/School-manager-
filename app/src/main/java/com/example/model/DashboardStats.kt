package com.example.model

data class SubjectAverage(
    val subjectId: String,
    val subjectName: String,
    val coefficient: Long,
    val averageOn20: Double,
    val evaluationCount: Int
)

data class StudentTermReport(
    val student: Student,
    val term: TermPeriod,
    val subjectAverages: List<SubjectAverage>,
    val weightedAverage: Double,
    val rank: Int,
    val classSize: Int,
    val mention: String
)

data class StudentBalance(
    val student: Student,
    val expectedFcfa: Long,
    val paidFcfa: Long,
    val remainingFcfa: Long
) {
    val isPaidInFull: Boolean get() = remainingFcfa <= 0L
}

data class MonthlyMetric(
    val label: String,
    val value: Float
)

data class ActivityFeedItem(
    val title: String,
    val subtitle: String,
    val timeLabel: String,
    val category: String
)

data class DashboardStats(
    val totalStudents: Int = 0,
    val totalTeachers: Int = 0,
    val totalClasses: Int = 0,
    val totalCollectedFcfa: Long = 0L,
    val totalExpectedFcfa: Long = 0L,
    val remainingToCollectFcfa: Long = 0L,
    val collectionRatePercent: Float = 0f,
    val attendanceRatePercent: Float = 0f,
    val enrollmentSeries: List<MonthlyMetric> = emptyList(),
    val paymentByMethod: Map<PaymentMethod, Long> = emptyMap(),
    val unpaidStudents: List<StudentBalance> = emptyList(),
    val recentActivities: List<ActivityFeedItem> = emptyList()
)
