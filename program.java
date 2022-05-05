import java.sql.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Scanner;

public class program {
    // Get all the appointments associated with a midwife on a user-specified date
    private static String[] getAppointment(Connection con, String inputID, String inputDate) throws SQLException {
        Boolean decided = false;
        Boolean chooseAnother = false;
        Integer inspect = 0;    // Store the appointment index the user wants to perform actions on
        List<Integer> aids = new ArrayList<>(); // Store assignment IDs for easy retrieval of appointment information
        while (!decided) {      // Repeating the loop until getting an actual date, not a 'D' or an 'E'
            Scanner scanner = new Scanner(System.in);
            if (inputDate == null || chooseAnother) {  // Skip this dialogue only if option 5 is called
                System.out.println("Please enter the date for appointment list [E] to exit:");
                inputDate = scanner.nextLine();
            }
            if (inputDate.equals("E")) {
                con.close();
                System.exit(0);
            }
            Statement getAppointments = con.createStatement();
            ResultSet appointments = getAppointments.executeQuery(
                    "SELECT ATIME, MNAME, MOTHER.MID, PRIMARYID, BACKUPID, AID " +
                            "FROM APPOINTMENT JOIN PREGNANCY ON APPOINTMENT.PREGID = PREGNANCY.PREGID " +
                            "JOIN MOTHER ON PREGNANCY.MID = MOTHER.MID " +
                            "WHERE ADATE = '" + inputDate + "' ORDER BY ATIME"
            );
            Integer i = 1;
            while (appointments.next()) {
                Integer primaryID = appointments.getInt("PRIMARYID");
                Integer backupID = appointments.getInt("BACKUPID");
                Time time = appointments.getTime("ATIME");
                String mother = appointments.getString("MNAME");
                Integer motherID = appointments.getInt("MID");
                Integer assignmentID = appointments.getInt("AID");
                aids.add(assignmentID);
                if (Integer.parseInt(inputID) == primaryID) {
                    System.out.println(i + ": " + time + " P " + mother + " " + motherID + "\n");
                    i++;
                } else if (Integer.parseInt(inputID) == backupID) {
                    System.out.println(i + ": " + time + " B " + mother + " " + motherID + "\n");
                    i++;
                }
            }
            appointments.close();
            System.out.println("Enter the appointment number that you would like to work on.\n" +
                    "[E] to exit [D] to go back to another date :");
            String option = scanner.nextLine();
            if (option.equals("E")) {
                con.close();
                System.exit(0);
            } else if (option.equals("D")) {
                chooseAnother = true;
                continue;
            } else {
                inspect = Integer.parseInt(option);
                decided = true;
            }
        }
        return new String[] {aids.get(inspect - 1).toString(), inputDate};
    }

    // Get the number of entries from a given table to facilitate determining the IDs new records should take
    private static Integer getCount(Connection con, String tbl) throws SQLException {
        Statement getNum = con.createStatement();
        ResultSet apps = getNum.executeQuery("SELECT COUNT(*) AS COUNT FROM " + tbl);
        Integer count = 0;
        while (apps.next()){
            count = apps.getInt("COUNT");
        }
        return count;
    }

    public static void main(String[] args) throws SQLException{
        DriverManager.registerDriver(new com.ibm.db2.jcc.DB2Driver());
        String id = System.getenv("SOCSUSER");
        String pwd = System.getenv("SOCSPASSWD");
        String url = System.getenv("DBURL");
        Connection con = DriverManager.getConnection(
                url, id, pwd
        );
        Boolean validId = false;
        String inputID = "";
        while (!validId) {      // Keep asking for practitioner ID, until a valid ID is received
            Scanner scanner = new Scanner(System.in);
            System.out.println("Please enter your practitioner id [E] to exit:");
            inputID = scanner.nextLine();
            if (inputID.equals("E")) {
                con.close();
                System.exit(0);
            } else {
                Statement idCheck = con.createStatement();
                ResultSet ids = idCheck.executeQuery("SELECT MWID FROM MIDWIFE");
                while (ids.next()) {
                    Integer mwid = ids.getInt("MWID");
                    if (mwid == Integer.parseInt(inputID)) {
                        validId = true;
                        break;
                    }
                }
                ids.close();
                if (!validId) {
                    System.out.println("The practitioner id is invalid. Try again!");
                }
            }
        }
        String inputDate = null;
        while (true) {  // Keep looping unless the user exit the program from the `getAppointment` function
            String[] resultReceived = getAppointment(con, inputID, inputDate);
            Integer aid = Integer.parseInt(resultReceived[0]);     // from the `getAppointment` function
            inputDate = resultReceived[1];
            Integer pregID = -1;
            Statement inspectAppointment = con.createStatement();
            ResultSet target = inspectAppointment.executeQuery(
                    "SELECT MNAME, MOTHER.MID, PREGNANCY.PREGID FROM APPOINTMENT JOIN PREGNANCY " +
                            "ON APPOINTMENT.PREGID = PREGNANCY.PREGID " +
                            "JOIN MOTHER ON PREGNANCY.MID = MOTHER.MID WHERE AID = " + aid);
            while (target.next()) {
                String mother = target.getString("MNAME");
                Integer motherID = target.getInt("MID");
                pregID = target.getInt("PREGID");
                System.out.println("For " + mother + " " + motherID);
            }
            target.close();
            Boolean optionFive = false;     // Keep looping the five-option menu, unless option 5 is chosen
            while (!optionFive) {
                System.out.println("\n1. Review notes\n2. Review tests\n3. Add a note\n4. Prescribe a test\n" +
                        "5. Go back to the appointments\n\nEnter your choice:");
                Scanner scanner = new Scanner(System.in);
                String inputOption = scanner.nextLine();
                switch (Integer.parseInt(inputOption)) {
                    case 1:
                        Statement getNotes = con.createStatement();
                        ResultSet notes = getNotes.executeQuery(
                                "SELECT NDATE, NTIME, CONTENT FROM NOTE JOIN APPOINTMENT ON " +
                                        "APPOINTMENT.AID = NOTE.AID WHERE APPOINTMENT.PREGID = " + pregID +
                                        " ORDER BY NDATE DESC, NTIME DESC"
                        );
                        while (notes.next()) {
                            Date date = notes.getDate("NDATE");
                            Time time = notes.getTime("NTIME");
                            String content = notes.getString("CONTENT");
                            // Truncate the note content to show the first 50 characters only
                            Integer maxLen = Math.min(content.length(), 50);
                            System.out.println(String.format("%s %s %s", date, time, content.substring(0, maxLen)));
                        }
                        notes.close();
                        break;
                    case 2:
                        Statement getTests = con.createStatement();
                        ResultSet tests = getTests.executeQuery(
                                "SELECT PRESDATE, TTYPE, COALESCE(RESULT, 'PENDING') AS RESULT FROM TEST " +
                                        "JOIN PREGNANCY ON " +
                                        "PREGNANCY.PREGID = TEST.PREGID WHERE PREGNANCY.PREGID = " + pregID +
                                        " ORDER BY PRESDATE DESC"
                        );
                        while (tests.next()) {
                            Date presDate = tests.getDate("PRESDATE");
                            String type = tests.getString("TTYPE");
                            String result = tests.getString("RESULT");
                            // Truncate the result to show the first 50 characters only
                            Integer max = Math.min(result.length(), 50);
                            System.out.println(String.format("%s [%s] %s", presDate, type, result.substring(0, max)));
                        }
                        tests.close();
                        break;
                    case 3:
                        System.out.println("Please type your observation:");
                        String observation = scanner.nextLine();
                        Integer appointmentCount = getCount(con, "APPOINTMENT");
                        Statement addAppointment = con.createStatement();
                        addAppointment.executeUpdate(String.format("INSERT INTO APPOINTMENT VALUES " +
                                "(%d, '2022-09-09', '10:00:00', %s, %d)", appointmentCount + 51, inputID, pregID));
                        Integer noteCount = getCount(con, "NOTE");
                        Statement addNote = con.createStatement();
                        addNote.executeUpdate(String.format("INSERT INTO NOTE VALUES (%d, '2022-10-10', '11:00:00', " +
                                "'%s', %d)", noteCount + 61, observation, aid));
                        break;
                    case 4:
                        System.out.println("Please enter the type of test:");
                        String type = scanner.nextLine();
                        Integer appointmentCount1 = getCount(con, "APPOINTMENT");
                        Statement addAppointment1 = con.createStatement();
                        addAppointment1.executeUpdate(String.format("INSERT INTO APPOINTMENT " +
                                "VALUES (%d, '2022-11-11', '12:00:00', %s, %d)", appointmentCount1 + 51, inputID, pregID));
                        Integer testCount = getCount(con, "TEST");
                        Statement addTest = con.createStatement();
                        addTest.executeUpdate(String.format("INSERT INTO TEST VALUES " +
                                "(%d, '%s', '2022-12-12', '2022-12-12', '2023-02-02', " +
                                "'Placeholder', %d, NULL, 'Tech999', '999', 9999)", testCount + 71, type, pregID));
                        break;
                    case 5:
                        optionFive = true;
                        break;
                }
            }
        }
    }

}
