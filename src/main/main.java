package main;

import config.config;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class main {

    // --- Helper Methods ---

    public static void viewStudents(config conf) {
        String query = "SELECT s_id, s_name, s_gender FROM tbl_student";
        String[] headers = {"s_ID", "s_Name", "s_Gender"};
        String[] columns = {"s_id", "s_name", "s_gender"};
        conf.viewRecords(query, headers, columns);
    }
    
    /**
     * Retrieves the maximum (most recent) v_id from tbl_violation.
     * This is crucial for linking the new record to tbl_records after insertion.
     */
    private static int getLatestViolationId(config con) {
        String qry = "SELECT MAX(v_id) AS last_id FROM tbl_violation";
        List<Map<String, Object>> result = con.fetchRecords(qry);
        // The check below handles cases where the table is empty (MAX returns null) or the record failed to insert.
        if (!result.isEmpty() && result.get(0).get("last_id") instanceof Number) {
            return ((Number) result.get(0).get("last_id")).intValue();
        }
        return -1; 
    }
    
    // --- Violation Management Functions ---
    
    /**
     * Retrieves and displays ALL violation records by joining the split tables.
     * Used by Admin and Teacher.
     */
    public static void viewViolations(config conf) {
        // T1: tbl_violation (details & confrontation date)
        // T2: tbl_records (record date)
        // NOTE: T1.v_type is now selected and displayed.
        String query = "SELECT T1.v_id, T3.s_name, T1.v_desc, T1.v_type, T1.v_severity, T1.v_penalty, T2.r_date, T1.v_date_confronted, T4.u_name AS Recorded_By " +
                        "FROM tbl_violation T1 " +
                        "JOIN tbl_records T2 ON T1.v_id = T2.v_id " + 
                        "JOIN tbl_student T3 ON T1.s_id = T3.s_id " + 
                        "JOIN tbl_user T4 ON T1.u_id = T4.u_id";     
        
        // UPDATED: Added "Type" to headers and "v_type" to columns
        String[] headers = {"V_ID", "Student Name", "Description", "Type", "Severity", "Penalty", "Recorded Date", "Confront Date", "Recorded By"};
        String[] columns = {"v_id", "s_name", "v_desc", "v_type", "v_severity", "v_penalty", "r_date", "v_date_confronted", "Recorded_By"};
        conf.viewRecords(query, headers, columns);
    }
    
    /**
     * Handles the process of adding a record, split between tbl_violation and tbl_records.
     * This function is used by the Teacher.
     */
    public static void addViolation(config con, Scanner sc, int teacherId) {
        System.out.println("\n--- ADD NEW VIOLATION RECORD ---");
        
        viewStudents(con);
        
        int sId = -1;
        while (sId < 0) {
            System.out.print("Enter Student ID (s_id) to assign violation: ");
            if (sc.hasNextInt()) {
                sId = sc.nextInt();
                sc.nextLine();  
                
                String studentCheckQry = "SELECT * FROM tbl_student WHERE s_id = ?";
                if (con.fetchRecords(studentCheckQry, sId).isEmpty()) {
                    System.out.println("Error: Student ID not found! Please try again.");
                    sId = -1;
                }
            } else {
                System.out.println("Invalid input. Please enter a number.");
                sc.nextLine();  
            }
        }

        System.out.print("Enter Violation Description (v_desc): ");
        String vDesc = sc.nextLine();
        
        // NEW: Get Violation Type Input
        System.out.print("Enter Violation Type (v_type, e.g., Academic, Behavioral): ");
        String vType = sc.nextLine();
        // END NEW

        System.out.print("Enter Violation Severity (e.g., Low, Medium, High): ");
        String vSeverity = sc.nextLine();
        
        System.out.print("Enter Penalty: ");
        String vPenalty = sc.nextLine();
        
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String recordedDate = now.format(formatter);
        
        // 1. Insert details (with v_date_confronted = NULL) into tbl_violation
        // UPDATED: Added v_type to the column list
        String sqlInsertViolation = "INSERT INTO tbl_violation (s_id, u_id, v_desc, v_type, v_penalty, v_severity, v_date_confronted) VALUES (?, ?, ?, ?, ?, ?, NULL)";
        // UPDATED: Added vType to the parameters
        con.addRecord(sqlInsertViolation, sId, teacherId, vDesc, vType, vPenalty, vSeverity);
        
        // 2. Retrieve the new v_id (Crucial link step)
        int newVId = getLatestViolationId(con);
        
        if (newVId > 0) {
            // 3. Insert record log (only v_id and r_date) into tbl_records
            String sqlInsertRecord = "INSERT INTO tbl_records (v_id, r_date) VALUES (?, ?)";
            con.addRecord(sqlInsertRecord, newVId, recordedDate);
            System.out.println("Violation record added successfully with V_ID: " + newVId);
        } else {
            // This error occurs if the previous con.addRecord failed due to the missing columns
            System.err.println("CRITICAL ERROR: Failed to retrieve new V_ID for record logging.");
        }
    }
    
    // --- Shared Functions (Original/Non-Violation Related) ---
    
    public static void viewUsers(config conf) {
        String Query = "SELECT * FROM tbl_user";
        String[] headers = {"ID", "Name", "Email", "Type", "Status"};
        String[] columns = {"u_id", "u_name", "u_email", "u_type", "u_status"};
        conf.viewRecords(Query, headers, columns);
    }
    
    public static void viewSubjects(config conf) {
        String Query = "SELECT * FROM tbl_subjects";
        String[] headers = {"Subject ID", "Code", "Description"};
        String[] columns = {"sbj_id", "sbj_code", "sbj_desc"};
        conf.viewRecords(Query, headers, columns);
    }

    public static void viewUnconfrontedViolations(config conf) {
        // Selects records where the confrontation date is NULL from tbl_violation
        // NOTE: T1.v_type is now selected and displayed.
        String query = "SELECT T1.v_id, T3.s_name, T1.v_desc, T1.v_type, T1.v_severity, T1.v_penalty, T2.r_date, T4.u_name AS Recorded_By " +
                        "FROM tbl_violation T1 " +
                        "JOIN tbl_records T2 ON T1.v_id = T2.v_id " + 
                        "JOIN tbl_student T3 ON T1.s_id = T3.s_id " + 
                        "JOIN tbl_user T4 ON T1.u_id = T4.u_id " + 
                        "WHERE T1.v_date_confronted IS NULL";
        
        // UPDATED: Added "Type" to headers and "v_type" to columns
        String[] headers = {"V_ID", "Student Name", "Description", "Type", "Severity", "Penalty", "Recorded Date", "Recorded By"};
        String[] columns = {"v_id", "s_name", "v_desc", "v_type", "v_severity", "v_penalty", "r_date", "Recorded_By"};
        
        System.out.println("\n--- UNCONFRONTED VIOLATIONS READY FOR ACTION ---");
        conf.viewRecords(query, headers, columns);
    }

    public static void updateViolation(config con, Scanner sc) {
        System.out.println("\n--- MARK VIOLATION AS CONFRONTED ---");
        
        viewUnconfrontedViolations(con);
        
        System.out.print("Enter Violation ID (v_id) from the list above to mark as confronted (or 0 to cancel): ");
        int vId = -1;
        if (sc.hasNextInt()) {
            vId = sc.nextInt();
        }
        sc.nextLine();  
        
        if (vId <= 0) {
            System.out.println("Operation canceled. Returning to menu.");
            return;
        }

        
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String confrontDate = now.format(formatter);
        
        System.out.println("Attempting to mark Violation ID " + vId + " as confronted on: " + confrontDate);
        
        
        String sqlUpdate = "UPDATE tbl_violation SET v_date_confronted = ? WHERE v_id = ?";
        con.updateRecord(sqlUpdate, confrontDate, vId);
    }

    // --- Main method ---

    public static void main(String[] args) {
        config con = new config();
        con.connectDB();

        Scanner sc = new Scanner(System.in);
        char cont;

        do {
            System.out.println("\n===== STUDENT VIOLATION SYSTEM =====");
            System.out.println("1. Login");
            System.out.println("2. Register");
            System.out.println("3. Exit");
            System.out.print("Enter choice: ");
            int choice = sc.nextInt();
            sc.nextLine();

            switch (choice) {
                case 1:
                    System.out.print("Enter email: ");
                    String em = sc.next();
                    System.out.print("Enter Password: ");
                    String pas = sc.next();

                    String qry = "SELECT * FROM tbl_user WHERE u_email = ? AND u_pass = ?";
                    List<Map<String, Object>> result = con.fetchRecords(qry, em, pas);

                    if (result.isEmpty()) {
                        System.out.println("INVALID CREDENTIALS");
                    } else {
                        Map<String, Object> user = result.get(0);
                        String stat = user.get("u_status").toString();
                        String utype = user.get("u_type").toString();
                        
                        Integer userId; 
                        Object uIdObject = user.get("u_id");
                        if (uIdObject instanceof Number) {
                             userId = ((Number) uIdObject).intValue();
                        } else {
                            System.err.println("Error: u_id could not be converted to Integer.");
                            break; 
                        }
                        
                        if (stat.equalsIgnoreCase("Pending")) {
                            System.out.println("Account is Pending, Contact the Admin!");
                        } else {
                            System.out.println("LOGIN SUCCESS!");
                            switch (utype) {
                                case "Admin":
                                    System.out.println("WELCOME TO ADMIN DASHBOARD");
                                    System.out.println("1. Approve Account!");
                                    System.out.println("2. Add Student!"); 
                                    System.out.println("3. View All Students");
                                    System.out.println("4. View All Violation Records");
                                    System.out.println("5. Logout");
                                    System.out.print("Enter option: ");
                                    int adminOption = sc.nextInt();
                                    sc.nextLine();
                                    
                                    switch (adminOption) {
                                        case 1:
                                            viewUsers(con);
                                            System.out.print("Enter ID to Approve: ");
                                            int ids = sc.nextInt();
                                            sc.nextLine();
                                            String sql = "UPDATE tbl_user SET u_status = ? WHERE u_id = ?";
                                            con.updateRecord(sql, "Approved", ids);
                                            break;
                                        
                                        case 2: // Add Student
                                            System.out.print("Enter Student Name: ");
                                            String sName = sc.next();
                                            System.out.print("Enter Student Gender (Male/Female/Other): ");
                                            String sGender = sc.next();
                                            
                                            String sqlInsertStudent = "INSERT INTO tbl_student (s_name, s_gender, u_id) VALUES(?,?,?)";
                                            con.addRecord(sqlInsertStudent, sName, sGender, userId); 
                                            break;

                                        case 3: // View Students
                                            viewStudents(con);
                                            break;
                                            
                                        case 4: // View Violations
                                            viewViolations(con);
                                            break;

                                        case 5:
                                            System.out.println("Logged out successfully!");
                                            break;
                                            
                                        default:
                                            System.out.println("Invalid option!");
                                    }
                                    break;

                                case "Teacher":
                                    System.out.println("WELCOME TO TEACHER DASHBOARD");
                                    System.out.println("1. Add Violation Record");
                                    System.out.println("2. View All Recorded Records");
                                    System.out.println("3. Logout");
                                    System.out.print("Enter option: ");
                                    int teacherOption = sc.nextInt();
                                    sc.nextLine();
                                    
                                    switch (teacherOption) {
                                        case 1:
                                            addViolation(con, sc, userId);
                                            break;
                                        case 2:
                                            viewViolations(con);
                                            break;
                                        case 3:
                                            System.out.println("Logged out successfully!");
                                            break;
                                        default:
                                            System.out.println("Invalid option!");
                                    }
                                    break;

                                case "Counselor":
                                    System.out.println("WELCOME TO COUNSELOR DASHBOARD");
                                    System.out.println("1. Add Student"); 
                                    System.out.println("2. View Students");
                                    System.out.println("3. Update Student");
                                    System.out.println("4. Delete Student");
                                    System.out.println("5. View All Violation Records"); 
                                    System.out.println("6. Mark Violation as Confronted"); 
                                    System.out.println("7. Logout"); 
                                    System.out.print("Enter option: ");
                                    int option = sc.nextInt();
                                    sc.nextLine();

                                    switch (option) {
                                        case 1: // Add Student
                                            System.out.print("Enter Student Name: ");
                                            String name = sc.next();
                                            System.out.print("Enter Student Gender (Male/Female/Other): ");
                                            String gender = sc.next();
                                            
                                            // Using Counselor's userId as the FK
                                            String sqlInsert = "INSERT INTO tbl_student (s_name, s_gender, u_id) VALUES(?,?,?)";
                                            con.addRecord(sqlInsert, name, gender, userId); 
                                            break;

                                        case 2: // View Students 
                                            viewStudents(con);
                                            break;

                                        case 3: // Update Student
                                            viewStudents(con); // Show students first
                                            System.out.print("Enter Student ID to Update: ");
                                            int idUpdate = sc.nextInt();
                                            sc.nextLine();
                                            System.out.print("Enter New Name: ");
                                            String nname = sc.next();
                                            System.out.print("Enter New Gender (Male/Female/Other): ");
                                            String ngender = sc.next(); 
                                            
                                            String qryUpdate = "UPDATE tbl_student SET s_name=?, s_gender=? WHERE s_id=?";
                                            con.updateRecord(qryUpdate, nname, ngender, idUpdate);
                                            break;

                                        case 4: // Delete Student
                                            viewStudents(con); // Show students first
                                            System.out.print("Enter Student ID to Delete: ");
                                            int idDelete = sc.nextInt();
                                            sc.nextLine();
                                            
                                            // Check for dependent records (Violations) before deleting a student
                                            String checkQry = "SELECT COUNT(*) AS count FROM tbl_violation WHERE s_id = ?";
                                            List<Map<String, Object>> countResult = con.fetchRecords(checkQry, idDelete);
                                            Number countNumber = (Number) countResult.get(0).get("count");
                                            int count = (countNumber != null) ? countNumber.intValue() : 0;
                                            
                                            if (count > 0) {
                                                System.out.println("Error: Cannot delete Student ID " + idDelete + " because " + count + " violation record(s) exist.");
                                                break;
                                            }
                                            
                                            String qryDelete = "DELETE FROM tbl_student WHERE s_id=?";
                                            con.deleteRecord(qryDelete, idDelete);
                                            break;
                                            
                                        case 5: // View All Violation Records
                                            viewViolations(con);
                                            break;

                                        case 6: // Update Violation Record (Mark Confronted Date on tbl_violation)
                                            updateViolation(con, sc);
                                            break;

                                        case 7: // Logout
                                            System.out.println("Logged out successfully!");
                                            break;

                                        default:
                                            System.out.println("Invalid option!");
                                    }
                                    break;
                            }
                        }
                    }
                    break;

                case 2:
                    System.out.print("Enter user name: ");
                    String name = sc.next();
                    System.out.print("Enter user email: ");
                    String email = sc.next();

                    while (true) {
                        String qry2 = "SELECT * FROM tbl_user WHERE u_email = ?";
                        List<Map<String, Object>> result2 = con.fetchRecords(qry2, email);
                        if (result2.isEmpty()) break;
                        System.out.print("Email already exists, Enter another Email: ");
                        email = sc.next();
                    }

                    System.out.print("Enter user Type (1 - Admin, 2 - Counselor, 3 - Teacher): ");
                    int typeNum = sc.nextInt();
                    sc.nextLine();

                    while (typeNum < 1 || typeNum > 3) {
                         System.out.print("Invalid, choose between 1 and 3 only: ");
                         typeNum = sc.nextInt();
                         sc.nextLine();
                    }

                    String tp;
                    if (typeNum == 1){ 
                        tp = "Admin";
                    }else if (typeNum == 2){
                        tp = "Counselor";
                    }else{ 
                        tp = "Teacher";
                    }
                    System.out.print("Enter Password: ");
                    String pass = sc.next();

                    String sqlReg = "INSERT INTO tbl_user(u_name, u_email, u_type, u_status, u_pass) VALUES (?, ?, ?, ?, ?)";
                    con.addRecord(sqlReg, name, email, tp, "Pending", pass);
                    break;

                case 3:
                    System.out.println("Thank you! Program ended.");
                    System.exit(0);
                    break;

                default:
                    System.out.println("Invalid choice.");
            }

            System.out.print("Do you want to continue? (Y/N): ");
            cont = sc.next().charAt(0);
            sc.nextLine();

        } while (cont == 'Y' || cont == 'y');

        sc.close();
    }
}