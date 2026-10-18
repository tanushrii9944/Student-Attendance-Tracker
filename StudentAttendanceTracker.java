import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Student Attendance Tracker
 * --------------------------
 * A console-based application to record attendance subject-wise, calculate
 * attendance percentages and flag students who fall below the required
 * minimum (default 75%).
 *
 * Data is saved in three CSV files (students.csv, subjects.csv, attendance.csv)
 * so it persists between runs. Requires Java 8 or above. No external libraries.
 */
public class StudentAttendanceTracker {

    // ---------------------------------------------------------------
    // Models
    // ---------------------------------------------------------------
    static class Student {
        private final int id;
        private final String rollNo;
        private final String name;

        Student(int id, String rollNo, String name) {
            this.id = id;
            this.rollNo = rollNo;
            this.name = name;
        }

        int getId() { return id; }
        String getRollNo() { return rollNo; }
        String getName() { return name; }

        String toCsv() {
            return id + "," + clean(rollNo) + "," + clean(name);
        }

        static Student fromCsv(String line) {
            String[] p = line.split(",");
            if (p.length != 3) {
                throw new IllegalArgumentException("Invalid student record: " + line);
            }
            return new Student(Integer.parseInt(p[0].trim()), p[1].trim(), p[2].trim());
        }
    }

    static class AttendanceRecord {
        private final LocalDate date;
        private final String subject;
        private final int studentId;
        private final boolean present;

        AttendanceRecord(LocalDate date, String subject, int studentId, boolean present) {
            this.date = date;
            this.subject = subject;
            this.studentId = studentId;
            this.present = present;
        }

        LocalDate getDate() { return date; }
        String getSubject() { return subject; }
        int getStudentId() { return studentId; }
        boolean isPresent() { return present; }

        String toCsv() {
            return date + "," + clean(subject) + "," + studentId + "," + (present ? "P" : "A");
        }

        static AttendanceRecord fromCsv(String line) {
            String[] p = line.split(",");
            if (p.length != 4) {
                throw new IllegalArgumentException("Invalid attendance record: " + line);
            }
            return new AttendanceRecord(
                    LocalDate.parse(p[0].trim()), p[1].trim(),
                    Integer.parseInt(p[2].trim()), p[3].trim().equalsIgnoreCase("P"));
        }
    }

    private static String clean(String s) {
        return s.replace(",", " ").trim();
    }

    // ---------------------------------------------------------------
    // Attendance manager (business logic + persistence)
    // ---------------------------------------------------------------
    static class AttendanceManager {
        private final List<Student> students = new ArrayList<>();
        private final List<String> subjects = new ArrayList<>();
        private final List<AttendanceRecord> records = new ArrayList<>();
        private final Path studentPath;
        private final Path subjectPath;
        private final Path attendancePath;
        private int nextId = 1;
        private int threshold = 75; // minimum required attendance in percent

        AttendanceManager(String studentFile, String subjectFile, String attendanceFile) {
            this.studentPath = Paths.get(studentFile);
            this.subjectPath = Paths.get(subjectFile);
            this.attendancePath = Paths.get(attendanceFile);
            load();
        }

        int getThreshold() { return threshold; }
        void setThreshold(int threshold) { this.threshold = threshold; }

        List<Student> getStudents() {
            return students.stream()
                    .sorted(Comparator.comparing(Student::getRollNo, String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toList());
        }

        List<String> getSubjects() {
            return new ArrayList<>(subjects);
        }

        Student findByRoll(String rollNo) {
            for (Student s : students) {
                if (s.getRollNo().equalsIgnoreCase(rollNo.trim())) return s;
            }
            return null;
        }

        String addStudent(String rollNo, String name) {
            if (findByRoll(rollNo) != null) return "A student with this roll number already exists.";
            students.add(new Student(nextId++, rollNo.trim(), name.trim()));
            saveAll();
            return null;
        }

        boolean removeStudent(String rollNo) {
            Student s = findByRoll(rollNo);
            if (s == null) return false;
            students.remove(s);
            records.removeIf(r -> r.getStudentId() == s.getId());
            saveAll();
            return true;
        }

        String addSubject(String name) {
            for (String s : subjects) {
                if (s.equalsIgnoreCase(name.trim())) return "This subject already exists.";
            }
            subjects.add(name.trim());
            saveAll();
            return null;
        }

        boolean hasSession(String subject, LocalDate date) {
            for (AttendanceRecord r : records) {
                if (r.getSubject().equalsIgnoreCase(subject) && r.getDate().equals(date)) return true;
            }
            return false;
        }

        /** Saves one class session. Any earlier entry for the same subject + date is replaced. */
        void markSession(String subject, LocalDate date, Set<Integer> absentIds) {
            records.removeIf(r -> r.getSubject().equalsIgnoreCase(subject) && r.getDate().equals(date));
            for (Student s : students) {
                records.add(new AttendanceRecord(date, subject, s.getId(), !absentIds.contains(s.getId())));
            }
            saveAll();
        }

        /** Returns {present, total}. Pass subject = null for all subjects combined. */
        int[] stats(int studentId, String subject) {
            int present = 0;
            int total = 0;
            for (AttendanceRecord r : records) {
                if (r.getStudentId() != studentId) continue;
                if (subject != null && !r.getSubject().equalsIgnoreCase(subject)) continue;
                total++;
                if (r.isPresent()) present++;
            }
            return new int[]{present, total};
        }

        static double percent(int[] s) {
            return s[1] == 0 ? -1 : s[0] * 100.0 / s[1];
        }

        boolean isLow(int[] s) {
            return s[1] > 0 && percent(s) < threshold;
        }

        /** Tells the student how many classes to attend (or can miss) to stay at the threshold. */
        String advice(int[] s) {
            int present = s[0];
            int total = s[1];
            if (total == 0) return "";
            if (isLow(s)) {
                double need = (threshold * (double) total - 100.0 * present) / (100.0 - threshold);
                return "Attend next " + (long) Math.ceil(need - 1e-9) + " class(es) to reach " + threshold + "%";
            }
            double canMiss = (100.0 * present - threshold * (double) total) / threshold;
            long miss = (long) Math.floor(canMiss + 1e-9);
            return miss <= 0 ? "Do not miss any class" : "Can miss up to " + miss + " class(es)";
        }

        int sessionCount(String subject) {
            Set<LocalDate> dates = new HashSet<>();
            for (AttendanceRecord r : records) {
                if (r.getSubject().equalsIgnoreCase(subject)) dates.add(r.getDate());
            }
            return dates.size();
        }

        // ---------- persistence ----------
        private void load() {
            try {
                if (Files.exists(studentPath)) {
                    for (String line : Files.readAllLines(studentPath, StandardCharsets.UTF_8)) {
                        if (line.trim().isEmpty()) continue;
                        try {
                            Student s = Student.fromCsv(line);
                            students.add(s);
                            nextId = Math.max(nextId, s.getId() + 1);
                        } catch (RuntimeException ex) {
                            System.out.println("Skipping bad student record -> " + ex.getMessage());
                        }
                    }
                }
                if (Files.exists(subjectPath)) {
                    for (String line : Files.readAllLines(subjectPath, StandardCharsets.UTF_8)) {
                        if (!line.trim().isEmpty()) subjects.add(line.trim());
                    }
                }
                if (Files.exists(attendancePath)) {
                    for (String line : Files.readAllLines(attendancePath, StandardCharsets.UTF_8)) {
                        if (line.trim().isEmpty()) continue;
                        try {
                            records.add(AttendanceRecord.fromCsv(line));
                        } catch (RuntimeException ex) {
                            System.out.println("Skipping bad attendance record -> " + ex.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("Could not read data files: " + e.getMessage());
            }
        }

        private void saveAll() {
            try {
                Files.write(studentPath,
                        students.stream().map(Student::toCsv).collect(Collectors.toList()),
                        StandardCharsets.UTF_8);
                Files.write(subjectPath,
                        subjects.stream().map(StudentAttendanceTracker::clean).collect(Collectors.toList()),
                        StandardCharsets.UTF_8);
                Files.write(attendancePath,
                        records.stream().map(AttendanceRecord::toCsv).collect(Collectors.toList()),
                        StandardCharsets.UTF_8);
            } catch (IOException e) {
                System.out.println("Could not save data: " + e.getMessage());
            }
        }
    }

    // ---------------------------------------------------------------
    // Console UI
    // ---------------------------------------------------------------
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        AttendanceManager manager = new AttendanceManager("students.csv", "subjects.csv", "attendance.csv");

        System.out.println("=========================================");
        System.out.println("        STUDENT ATTENDANCE TRACKER       ");
        System.out.println("=========================================");

        if (!manager.getStudents().isEmpty()) {
            showLowAttendance(manager);
        }

        boolean running = true;
        while (running) {
            printMenu(manager);
            int choice = readInt("Enter choice: ");
            switch (choice) {
                case 1: addStudent(manager); break;
                case 2: addSubject(manager); break;
                case 3: markAttendance(manager); break;
                case 4: studentReport(manager); break;
                case 5: subjectReport(manager); break;
                case 6: showLowAttendance(manager); break;
                case 7: listStudentsAndSubjects(manager); break;
                case 8: changeThreshold(manager); break;
                case 9: removeStudent(manager); break;
                case 0:
                    running = false;
                    System.out.println("Goodbye! Data saved.");
                    break;
                default:
                    System.out.println("Invalid choice. Try again.");
            }
        }
    }

    private static void printMenu(AttendanceManager m) {
        System.out.println("\n---------------- MENU ----------------");
        System.out.println("1. Add student");
        System.out.println("2. Add subject");
        System.out.println("3. Mark attendance");
        System.out.println("4. Student report");
        System.out.println("5. Subject report");
        System.out.println("6. Low attendance list");
        System.out.println("7. View students and subjects");
        System.out.println("8. Change minimum attendance (current: " + m.getThreshold() + "%)");
        System.out.println("9. Remove student");
        System.out.println("0. Exit");
        System.out.println("--------------------------------------");
    }

    private static void addStudent(AttendanceManager m) {
        System.out.println("\n-- Add Student --");
        String roll = readNonEmpty("Roll number: ");
        String name = readNonEmpty("Student name: ");
        String error = m.addStudent(roll, name);
        System.out.println(error == null ? "Student added." : error);
    }

    private static void addSubject(AttendanceManager m) {
        String name = readNonEmpty("Subject name: ");
        String error = m.addSubject(name);
        System.out.println(error == null ? "Subject added." : error);
    }

    private static void markAttendance(AttendanceManager m) {
        if (m.getStudents().isEmpty() || m.getSubjects().isEmpty()) {
            System.out.println("\nAdd at least one student and one subject first.");
            return;
        }
        String subject = pickSubject(m);
        if (subject == null) return;
        LocalDate date = readDateOrToday("Date (YYYY-MM-DD, press Enter for today): ");

        if (m.hasSession(subject, date)) {
            System.out.print("Attendance for " + subject + " on " + date
                    + " is already marked. Replace it? (y/n): ");
            if (!scanner.nextLine().trim().equalsIgnoreCase("y")) {
                System.out.println("Cancelled.");
                return;
            }
        }

        Set<Integer> absent = readAbsentees(m);
        m.markSession(subject, date, absent);
        System.out.println("Saved: " + (m.getStudents().size() - absent.size()) + " present, "
                + absent.size() + " absent.");
    }

    private static Set<Integer> readAbsentees(AttendanceManager m) {
        while (true) {
            System.out.print("Roll numbers of ABSENT students, comma separated (blank = everyone present): ");
            String line = scanner.nextLine().trim();
            Set<Integer> ids = new HashSet<>();
            if (line.isEmpty()) return ids;

            boolean valid = true;
            for (String part : line.split(",")) {
                Student s = m.findByRoll(part.trim());
                if (s == null) {
                    System.out.println("Unknown roll number: " + part.trim());
                    valid = false;
                    break;
                }
                ids.add(s.getId());
            }
            if (valid) return ids;
        }
    }

    private static void studentReport(AttendanceManager m) {
        Student s = m.findByRoll(readNonEmpty("Roll number: "));
        if (s == null) {
            System.out.println("Student not found.");
            return;
        }
        System.out.println("\n== ATTENDANCE REPORT: " + s.getName() + " (" + s.getRollNo() + ") ==");
        String fmt = "%-18s %-8s %-7s %-9s %-8s%n";
        System.out.printf(fmt, "SUBJECT", "PRESENT", "TOTAL", "PERCENT", "STATUS");
        System.out.println(repeat('-', 54));
        for (String subject : m.getSubjects()) {
            int[] st = m.stats(s.getId(), subject);
            printStatLine(fmt, trim(subject, 18), st, m);
        }
        int[] overall = m.stats(s.getId(), null);
        System.out.println(repeat('-', 54));
        printStatLine(fmt, "OVERALL", overall, m);
        if (overall[1] > 0) {
            System.out.println("Advice: " + m.advice(overall));
        }
    }

    private static void printStatLine(String fmt, String label, int[] st, AttendanceManager m) {
        if (st[1] == 0) {
            System.out.printf(fmt, label, "-", "-", "N/A", "-");
        } else {
            System.out.printf(fmt, label, st[0], st[1],
                    String.format("%.1f%%", AttendanceManager.percent(st)),
                    m.isLow(st) ? "LOW" : "OK");
        }
    }

    private static void subjectReport(AttendanceManager m) {
        String subject = pickSubject(m);
        if (subject == null) return;
        System.out.println("\n== SUBJECT REPORT: " + subject + " (" + m.sessionCount(subject)
                + " class(es) held) ==");
        String fmt = "%-8s %-20s %-8s %-7s %-9s %-8s%n";
        System.out.printf(fmt, "ROLL", "NAME", "PRESENT", "TOTAL", "PERCENT", "STATUS");
        System.out.println(repeat('-', 64));
        for (Student s : m.getStudents()) {
            int[] st = m.stats(s.getId(), subject);
            if (st[1] == 0) {
                System.out.printf(fmt, s.getRollNo(), trim(s.getName(), 20), "-", "-", "N/A", "-");
            } else {
                System.out.printf(fmt, s.getRollNo(), trim(s.getName(), 20), st[0], st[1],
                        String.format("%.1f%%", AttendanceManager.percent(st)),
                        m.isLow(st) ? "LOW" : "OK");
            }
        }
    }

    private static void showLowAttendance(AttendanceManager m) {
        System.out.println("\n== LOW ATTENDANCE (below " + m.getThreshold() + "%) ==");
        String fmt = "%-8s %-18s %-16s %-9s %-9s %s%n";
        boolean found = false;
        for (Student s : m.getStudents()) {
            for (String subject : m.getSubjects()) {
                int[] st = m.stats(s.getId(), subject);
                if (!m.isLow(st)) continue;
                if (!found) {
                    System.out.printf(fmt, "ROLL", "NAME", "SUBJECT", "PRESENT", "PERCENT", "ADVICE");
                    System.out.println(repeat('-', 90));
                    found = true;
                }
                System.out.printf(fmt, s.getRollNo(), trim(s.getName(), 18), trim(subject, 16),
                        st[0] + "/" + st[1],
                        String.format("%.1f%%", AttendanceManager.percent(st)), m.advice(st));
            }
        }
        if (!found) {
            System.out.println("No students are below the minimum. Great!");
        }
    }

    private static void listStudentsAndSubjects(AttendanceManager m) {
        System.out.println("\n== STUDENTS ==");
        if (m.getStudents().isEmpty()) {
            System.out.println("No students added yet.");
        }
        for (Student s : m.getStudents()) {
            System.out.println(s.getRollNo() + "  " + s.getName());
        }
        System.out.println("\n== SUBJECTS ==");
        if (m.getSubjects().isEmpty()) {
            System.out.println("No subjects added yet.");
        }
        for (String subject : m.getSubjects()) {
            System.out.println("- " + subject);
        }
    }

    private static void changeThreshold(AttendanceManager m) {
        while (true) {
            int value = readInt("New minimum attendance percent (1-99): ");
            if (value >= 1 && value <= 99) {
                m.setThreshold(value);
                System.out.println("Minimum attendance set to " + value + "%.");
                return;
            }
            System.out.println("Please enter a value between 1 and 99.");
        }
    }

    private static void removeStudent(AttendanceManager m) {
        String roll = readNonEmpty("Roll number to remove: ");
        System.out.println(m.removeStudent(roll)
                ? "Student and their attendance records removed."
                : "Student not found.");
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------
    private static String pickSubject(AttendanceManager m) {
        List<String> subjects = m.getSubjects();
        if (subjects.isEmpty()) {
            System.out.println("\nNo subjects added yet.");
            return null;
        }
        System.out.println("\nSubjects:");
        for (int i = 0; i < subjects.size(); i++) {
            System.out.println((i + 1) + ". " + subjects.get(i));
        }
        int pick = readInt("Select subject number (0 to cancel): ");
        if (pick < 1 || pick > subjects.size()) {
            if (pick != 0) System.out.println("Invalid selection.");
            return null;
        }
        return subjects.get(pick - 1);
    }

    private static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "~";
    }

    private static String repeat(char c, int n) {
        return new String(new char[n]).replace('\0', c);
    }

    private static String readNonEmpty(String prompt) {
        while (true) {
            System.out.print(prompt);
            String s = scanner.nextLine().trim();
            if (!s.isEmpty()) return s;
            System.out.println("Input cannot be empty.");
        }
    }

    private static int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                return Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number.");
            }
        }
    }

    private static LocalDate readDateOrToday(String prompt) {
        while (true) {
            System.out.print(prompt);
            String s = scanner.nextLine().trim();
            if (s.isEmpty()) return LocalDate.now();
            try {
                return LocalDate.parse(s);
            } catch (DateTimeParseException e) {
                System.out.println("Invalid date. Use format YYYY-MM-DD (e.g. 2026-10-18).");
            }
        }
    }
}
