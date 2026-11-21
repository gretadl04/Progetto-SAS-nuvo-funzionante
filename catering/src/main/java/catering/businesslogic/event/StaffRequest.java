package catering.businesslogic.event;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Objects;

import catering.businesslogic.shift.Shift;
import catering.businesslogic.user.User;
import catering.persistence.PersistenceManager;
import catering.persistence.ResultHandler;

/**
 * Represents a staffing requirement for a {@link Service} on a specific {@link Shift},
 * specifying the required role, headcount, and optionally an assigned {@link User}.
 */
public class StaffRequest {

    private int id;
    private int serviceId;
    private Shift shift;
    private String role;
    private int quantity;
    private User assignedUser;

    /** Creates an empty staff request (used during persistence loading). */
    public StaffRequest() {}

    /**
     * Creates a new staff request for a service shift.
     *
     * @param serviceId the ID of the owning {@link Service}
     * @param shift     the {@link Shift} during which staff is needed
     * @param role      the required role description (e.g., "Cameriere", "Cuoco")
     * @param quantity  the number of staff members requested
     */
    public StaffRequest(int serviceId, Shift shift, String role, int quantity) {
        this.serviceId = serviceId;
        this.shift = shift;
        this.role = role;
        this.quantity = quantity;
    }

    /** @return the database identifier of this staff request */
    public int getId() { return id; }

    /** @param id the database identifier to set */
    public void setId(int id) { this.id = id; }

    /** @return the owning service ID */
    public int getServiceId() { return serviceId; }

    /** @param serviceId the owning service ID to set */
    public void setServiceId(int serviceId) { this.serviceId = serviceId; }

    /** @return the associated {@link Shift} */
    public Shift getShift() { return shift; }

    /** @param shift the shift to associate */
    public void setShift(Shift shift) { this.shift = shift; }

    /** @return the requested role description */
    public String getRole() { return role; }

    /** @param role the role description to set */
    public void setRole(String role) { this.role = role; }

    /** @return the requested number of staff members */
    public int getQuantity() { return quantity; }

    /** @param quantity the requested quantity to set */
    public void setQuantity(int quantity) { this.quantity = quantity; }

    /** @return the assigned staff member, or {@code null} if unassigned */
    public User getAssignedUser() { return assignedUser; }

    /** @param assignedUser the staff member to assign */
    public void setAssignedUser(User assignedUser) { this.assignedUser = assignedUser; }

    // STATIC PERSISTENCE METHODS (called by EventPersistence observer)

    /**
     * Persists a new staff request in the database.
     *
     * @param sr the staff request to insert
     */
    public static void saveNewStaffRequest(StaffRequest sr) {
        int shiftId = (sr.shift != null) ? sr.shift.getId() : 0;
        int userId = (sr.assignedUser != null) ? sr.assignedUser.getId() : 0;
        String query = "INSERT INTO StaffRequests (service_id, shift_id, role, quantity, assigned_user_id) VALUES (?, ?, ?, ?, ?)";
        PersistenceManager.executeUpdate(query, sr.serviceId, shiftId, sr.role, sr.quantity, userId);
        sr.id = PersistenceManager.getLastId();
    }

    /**
     * Updates an existing staff request in the database.
     *
     * @param sr the staff request to update
     */
    public static void updateStaffRequest(StaffRequest sr) {
        int shiftId = (sr.shift != null) ? sr.shift.getId() : 0;
        int userId = (sr.assignedUser != null) ? sr.assignedUser.getId() : 0;
        String query = "UPDATE StaffRequests SET shift_id = ?, role = ?, quantity = ?, assigned_user_id = ? WHERE id = ?";
        PersistenceManager.executeUpdate(query, shiftId, sr.role, sr.quantity, userId, sr.id);
    }

    /**
     * Deletes a staff request from the database.
     *
     * @param sr the staff request to delete
     */
    public static void deleteStaffRequest(StaffRequest sr) {
        if (sr.id <= 0) return;
        String query = "DELETE FROM StaffRequests WHERE id = ?";
        PersistenceManager.executeUpdate(query, sr.id);
    }

    /**
     * Loads all staff requests associated with a given service.
     *
     * @param serviceId the service ID
     * @return list of persisted {@link StaffRequest} objects
     */
    public static ArrayList<StaffRequest> loadRequestsForService(int serviceId) {
        ArrayList<StaffRequest> list = new ArrayList<>();
        String query = "SELECT * FROM StaffRequests WHERE service_id = ?";
        PersistenceManager.executeQuery(query, new ResultHandler() {
            @Override
            public void handle(ResultSet rs) throws SQLException {
                StaffRequest sr = new StaffRequest();
                sr.id = rs.getInt("id");
                sr.serviceId = rs.getInt("service_id");
                sr.role = rs.getString("role");
                sr.quantity = rs.getInt("quantity");
                int shiftId = rs.getInt("shift_id");
                if (shiftId > 0) {
                    sr.shift = Shift.loadItemById(shiftId);
                }
                int userId = rs.getInt("assigned_user_id");
                if (userId > 0) {
                    sr.assignedUser = User.load(userId);
                }
                list.add(sr);
            }
        }, serviceId);
        return list;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        StaffRequest other = (StaffRequest) obj;
        if (this.id > 0 && other.id > 0) {
            return this.id == other.id;
        }
        return this.serviceId == other.serviceId &&
               this.quantity == other.quantity &&
               Objects.equals(this.role, other.role);
    }

    @Override
    public int hashCode() {
        return (id > 0) ? Integer.hashCode(id) : Objects.hash(serviceId, role, quantity);
    }
}