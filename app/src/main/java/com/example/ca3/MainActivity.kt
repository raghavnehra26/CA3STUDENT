package com.example.ca3

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Entity(tableName = "students")
data class Student(
    @PrimaryKey(autoGenerate = true)
    val studentId: Long = 0,
    val name: String
)

@Entity(
    tableName = "subjects",
    foreignKeys = [
        ForeignKey(
            entity = Student::class,
            parentColumns = ["studentId"],
            childColumns = ["studentOwnerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("studentOwnerId")]
)
data class Subject(
    @PrimaryKey(autoGenerate = true)
    val subjectId: Long = 0,
    val studentOwnerId: Long,
    val subjectName: String
)

data class StudentWithSubjects(
    @Embedded val student: Student,
    @Relation(
        parentColumn = "studentId",
        entityColumn = "studentOwnerId"
    )
    val subjects: List<Subject>
)

@Dao
interface StudentSubjectDao {

    @Insert
    suspend fun insertStudent(student: Student): Long

    @Insert
    suspend fun insertSubject(subject: Subject): Long

    @Query("SELECT COUNT(*) FROM subjects WHERE studentOwnerId = :studentId")
    suspend fun getSubjectCountForStudent(studentId: Long): Int

    @Transaction
    @Query("SELECT * FROM students ORDER BY studentId DESC")
    fun getStudentsWithSubjects(): Flow<List<StudentWithSubjects>>
}

@Database(
    entities = [Student::class, Subject::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun studentSubjectDao(): StudentSubjectDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "student_subject_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.getDatabase(this)
        val dao = db.studentSubjectDao()

        setContent {
            MaterialTheme {
                StudentSubjectManagerScreen(dao = dao)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentSubjectManagerScreen(dao: StudentSubjectDao) {
    val studentsWithSubjects by dao.getStudentsWithSubjects().collectAsState(initial = emptyList())
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var studentName by remember { mutableStateOf("") }
    var targetStudentId by remember { mutableStateOf("") }
    var subjectName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Student Subject Manager") }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Add Student",
                            style = MaterialTheme.typography.titleMedium
                        )
                        OutlinedTextField(
                            value = studentName,
                            onValueChange = { studentName = it },
                            label = { Text("Student Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                val trimmedName = studentName.trim()
                                if (trimmedName.isEmpty()) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Please enter a student name")
                                    }
                                    return@Button
                                }
                                coroutineScope.launch {
                                    val newId = dao.insertStudent(Student(name = trimmedName))
                                    targetStudentId = newId.toString()
                                    studentName = ""
                                    snackbarHostState.showSnackbar("Student added (ID: $newId)")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Add Student")
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Enroll Subject (Max 3 per Student)",
                            style = MaterialTheme.typography.titleMedium
                        )
                        OutlinedTextField(
                            value = targetStudentId,
                            onValueChange = { targetStudentId = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Student ID") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = subjectName,
                            onValueChange = { subjectName = it },
                            label = { Text("Subject Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                val id = targetStudentId.toLongOrNull()
                                val trimmedSubject = subjectName.trim()
                                if (id == null || trimmedSubject.isEmpty()) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Enter valid Student ID and Subject Name")
                                    }
                                    return@Button
                                }
                                coroutineScope.launch {
                                    val count = dao.getSubjectCountForStudent(id)
                                    if (count >= 3) {
                                        snackbarHostState.showSnackbar("Limit reached: Student already has 3 subjects")
                                    } else {
                                        dao.insertSubject(
                                            Subject(
                                                studentOwnerId = id,
                                                subjectName = trimmedSubject
                                            )
                                        )
                                        subjectName = ""
                                        snackbarHostState.showSnackbar("Subject added (${count + 1}/3)")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Add Subject")
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Students & Enrolled Subjects",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            if (studentsWithSubjects.isEmpty()) {
                item {
                    Text(
                        text = "No students added yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(studentsWithSubjects, key = { it.student.studentId }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${item.student.name} (ID: ${item.student.studentId})",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "${item.subjects.size}/3",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(8.dp))
                            if (item.subjects.isEmpty()) {
                                Text(
                                    text = "No subjects enrolled",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            } else {
                                item.subjects.forEach { subj ->
                                    Text(
                                        text = "• ${subj.subjectName}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
