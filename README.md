[README_AttendanceTracker.md](https://github.com/user-attachments/files/33628331/README_AttendanceTracker.md)

# Student Attendance Tracker

A lightweight, console-based Java application that records **subject-wise attendance**, calculates attendance percentages, and **flags students who fall below the required minimum (default 75%)** - with advice on how many classes they must attend to recover.

## Features

- Add and remove students (by roll number) and subjects
- Mark attendance quickly: just enter the roll numbers of absent students, and everyone else is marked present
- Re-marking a subject/date replaces the earlier entry (after confirmation)
- Student report: per-subject and overall attendance percentage
- Subject report: attendance of the whole class for one subject
- Low attendance list shown automatically on startup
- Smart advice for each student:
  - Below the minimum: *"Attend next 6 class(es) to reach 75%"*
  - Above the minimum: *"Can miss up to 2 class(es)"*
- Configurable minimum attendance (1-99%)
- Data saved in CSV files - no database required
- Input validation for dates, numbers and roll numbers

## Tech Stack

- Java 8 or above
- Java standard library only (no external dependencies)

## Getting Started

### Prerequisites
- JDK 8+ installed (`java -version` to check)

### Compile
```bash
javac StudentAttendanceTracker.java
```

### Run
```bash
java StudentAttendanceTracker
```

## Usage

```
=========================================
        STUDENT ATTENDANCE TRACKER
=========================================

---------------- MENU ----------------
1. Add student
2. Add subject
3. Mark attendance
4. Student report
5. Subject report
6. Low attendance list
7. View students and subjects
8. Change minimum attendance (current: 75%)
9. Remove student
0. Exit
--------------------------------------
```

### Sample output

```
== LOW ATTENDANCE (below 75%) ==
ROLL     NAME               SUBJECT          PRESENT   PERCENT   ADVICE
------------------------------------------------------------------------------------------
CS102    Priya              Mathematics      6/10      60.0%     Attend next 6 class(es) to reach 75%
CS105    Arjun              Physics          7/10      70.0%     Attend next 2 class(es) to reach 75%

== ATTENDANCE REPORT: Priya (CS102) ==
SUBJECT            PRESENT  TOTAL   PERCENT   STATUS
------------------------------------------------------
Mathematics        6        10      60.0%     LOW
Physics            9        10      90.0%     OK
------------------------------------------------------
OVERALL            15       20      75.0%     OK
Advice: Do not miss any class
```

## Project Structure

```
.
├── StudentAttendanceTracker.java   # Complete application source
├── README.md                       # Project documentation
├── students.csv                    # Auto-generated: student list
├── subjects.csv                    # Auto-generated: subject list
└── attendance.csv                  # Auto-generated: attendance records
```

## Data Format

`students.csv`
```
id,rollNo,name
1,CS101,Rahul
```

`attendance.csv`
```
date,subject,studentId,status
2026-10-18,Mathematics,1,P
```

## How It Works

1. Students and subjects are added once and saved to CSV files.
2. For each class, you pick the subject and date and enter the absentees.
3. Every student gets a Present/Absent record for that session.
4. Attendance % = classes attended / classes held for that student.
5. Anyone below the minimum is flagged, with the number of classes needed to recover:
   `needed = ceil((minimum x total - 100 x present) / (100 - minimum))`

## Future Improvements

- Email / SMS alerts to students or parents when attendance drops
- Class and semester grouping, multiple teachers
- Export reports to PDF / Excel
- Login for teachers and students
- GUI using JavaFX or Swing
- Database integration (MySQL / SQLite)

## License

This project is open source and available under the [MIT License](https://opensource.org/licenses/MIT).
