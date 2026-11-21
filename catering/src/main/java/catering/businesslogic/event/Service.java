package catering.businesslogic.event;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Objects;

import catering.businesslogic.menu.Menu;
import catering.persistence.PersistenceManager;
import catering.persistence.ResultHandler;

/**
 * Represents a catering service within an {@link Event} (e.g., lunch, coffee break, dinner),
 * including its time slot, approved menu, menu modification proposals, and {@link StaffRequest} list.
 */
public class Service {

    /**
     * Lifecycle states of a {@link Service}.
     */
    public enum State {
        ATTIVO("attivo"),
        ANNULLATO("annullato");

        private final String dbValue;

        State(String dbValue) {
            this.dbValue = dbValue;
        }

        /** @return the string representation stored in the database */
        public String getDbValue() {
            return dbValue;
        }

        /**
         * Parses a service state label from database or user input.
         *
         * @param value the string value to parse
         * @return the matching {@link State}, or {@code null} if unrecognized
         */
        public static State fromDbValue(String value) {
            if (value == null) return null;
            String normalized = value.trim();
            for (State s : values()) {
                if (s.dbValue.equalsIgnoreCase(normalized) || s.name().equalsIgnoreCase(normalized)) {
                    return s;
                }
            }
            return null;
        }
    }

    private int id;
    private String name;
    private Date date;
    private Time timeStart;
    private Time timeEnd;
    private String location;
    private int expectedParticipants;
    private State state;
    private int eventId;
    private Menu proposedMenu;
    private Menu menu;

    private boolean menuApproved;
    private String proposedMenuModifications;
    private ArrayList<StaffRequest> staffRequests;

    /**
     * Creates a new active service with an empty list of staff requests.
     */
    public Service() {
        this.staffRequests = new ArrayList<>();
        this.menuApproved = false;
        this.state = State.ATTIVO;
    }

    /**
     * Creates a new service with the given name.
     *
     * @param name the name or type of the service
     */
    public Service(String name) {
        this();
        this.name = name;
    }

    /** @return the database identifier of the service */
    public int getId() { return id; }

    /** @param id the database identifier to set */
    public void setId(int id) { this.id = id; }

    /** @return the service name */
    public String getName() { return name; }

    /** @param name the service name to set */
    public void setName(String name) { this.name = name; }

    /** @return the date of the service */
    public Date getDate() { return date; }

    /** @param date the service date to set */
    public void setDate(Date date) { this.date = date; }

    /** @return the start time of the service */
    public Time getTimeStart() { return timeStart; }

    /** @param timeStart the start time to set */
    public void setTimeStart(Time timeStart) { this.timeStart = timeStart; }

    /** @return the end time of the service */
    public Time getTimeEnd() { return timeEnd; }

    /** @param timeEnd the end time to set */
    public void setTimeEnd(Time timeEnd) { this.timeEnd = timeEnd; }

    /** @return the service location */
    public String getLocation() { return location; }

    /** @param location the service location to set */
    public void setLocation(String location) { this.location = location; }

    /** @return the expected number of participants for this service */
    public int getExpectedParticipants() { return expectedParticipants; }

    /** @param expectedParticipants the expected participants to set */
    public void setExpectedParticipants(int expectedParticipants) { this.expectedParticipants = expectedParticipants; }

    /** @return the typed state of this service */
    public State getServiceState() { return state; }

    /** @return the string label of the service state */
    public String getState() { return state != null ? state.getDbValue() : null; }

    /** @param state the typed state to set */
    public void setState(State state) {
        if (state != null) {
            this.state = state;
        }
    }

    /** @param state the string label of the state to set */
    public void setState(String state) {
        State parsed = State.fromDbValue(state);
        if (parsed != null) {
            this.state = parsed;
        }
    }

    /** @return the parent event ID */
    public int getEventId() { return eventId; }

    /** @param eventId the parent event ID to set */
    public void setEventId(int eventId) { this.eventId = eventId; }

    /** @return the menu proposed by the Chef */
    public Menu getProposedMenu() { return proposedMenu; }

    /** @return the proposed menu ID, or {@code 0} if none */
    public int getProposedMenuId() { return proposedMenu != null ? proposedMenu.getId() : 0; }

    /** @param proposedMenu the proposed menu to set */
    public void setProposedMenu(Menu proposedMenu) { this.proposedMenu = proposedMenu; }

    /** @return the approved menu associated with this service */
    public Menu getMenu() { return menu; }

    /** @return the approved menu ID, or {@code 0} if none */
    public int getMenuId() { return menu != null ? menu.getId() : 0; }

    /** @param menu the approved menu to set */
    public void setMenu(Menu menu) { this.menu = menu; }

    /** @return {@code true} if the menu has been approved by the Organizer */
    public boolean isMenuApproved() { return menuApproved; }

    /** @param menuApproved whether the menu is approved */
    public void setMenuApproved(boolean menuApproved) { this.menuApproved = menuApproved; }

    /** @return any menu modification proposals suggested by the Organizer */
    public String getProposedMenuModifications() { return proposedMenuModifications; }

    /** @param mods the menu modification proposals to set */
    public void setProposedMenuModifications(String mods) { this.proposedMenuModifications = mods; }

    /** @return the list of staff requests associated with this service */
    public ArrayList<StaffRequest> getStaffRequests() { return staffRequests; }

    /**
     * Adds a staff request to this service if not already present.
     *
     * @param sr the staff request to add
     */
    public void addStaffRequest(StaffRequest sr) {
        if (this.staffRequests == null) this.staffRequests = new ArrayList<>();
        if (sr != null && !this.staffRequests.contains(sr)) {
            this.staffRequests.add(sr);
        }
    }

    /**
     * Removes a staff request from this service.
     *
     * @param sr the staff request to remove
     * @return {@code true} if the request was present and removed
     */
    public boolean removeStaffRequest(StaffRequest sr) {
        if (this.staffRequests == null) return false;
        return this.staffRequests.remove(sr);
    }

    /**
     * Records a menu proposal prior to approval.
     *
     * @param m             the proposed menu
     * @param modifications optional modification notes
     */
    public void proposeMenu(Menu m, String modifications) {
        this.proposedMenu = m;
        this.menuApproved = false;
        this.proposedMenuModifications = modifications;
    }

    /**
     * Approves a menu for this service, optionally recording Organizer amendments.
     *
     * @param m             the menu to approve
     * @param modifications optional proposed modifications limited to this event
     */
    public void approveMenu(Menu m, String modifications) {
        this.menu = m;
        if (this.proposedMenu == null) {
            this.proposedMenu = m;
        }
        this.menuApproved = true;
        this.proposedMenuModifications = modifications;
    }

    // STATIC PERSISTENCE METHODS (called by EventPersistence observer)

    /**
     * Inserts a new service record into the database.
     *
     * @param s the service to persist
     */
    public static void saveNewService(Service s) {
        String query = "INSERT INTO Services (event_id, name, service_date, time_start, time_end, expected_participants, location, proposed_menu_id, approved_menu_id, state) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String dStr = (s.date != null) ? s.date.toString() : null;
        String tStart = (s.timeStart != null) ? s.timeStart.toString() : null;
        String tEnd = (s.timeEnd != null) ? s.timeEnd.toString() : null;

        PersistenceManager.executeUpdate(query, s.eventId, s.name, dStr, tStart, tEnd,
                s.expectedParticipants, s.location, s.getProposedMenuId(), s.getMenuId(), s.getState());
        s.id = PersistenceManager.getLastId();
    }

    /**
     * Updates an existing service record in the database.
     *
     * @param s the service to update
     */
    public static void updateService(Service s) {
        String query = "UPDATE Services SET name = ?, service_date = ?, time_start = ?, time_end = ?, expected_participants = ?, location = ?, proposed_menu_id = ?, approved_menu_id = ?, state = ? WHERE id = ?";
        String dStr = (s.date != null) ? s.date.toString() : null;
        String tStart = (s.timeStart != null) ? s.timeStart.toString() : null;
        String tEnd = (s.timeEnd != null) ? s.timeEnd.toString() : null;

        PersistenceManager.executeUpdate(query, s.name, dStr, tStart, tEnd,
                s.expectedParticipants, s.location, s.getProposedMenuId(), s.getMenuId(), s.getState(), s.id);
    }

    /**
     * Persists the menu approval and modification proposals for a service.
     *
     * @param s the service whose menu proposal should be saved
     */
    public static void saveMenuProposal(Service s) {
        int targetMenuId = (s.menu != null) ? s.getMenuId() : s.getProposedMenuId();
        PersistenceManager.executeUpdate("DELETE FROM MenuProposals WHERE service_id = ?", s.id);
        String query = "INSERT INTO MenuProposals (service_id, menu_id, approved, proposed_modifications) VALUES (?, ?, ?, ?)";
        PersistenceManager.executeUpdate(query, s.id, targetMenuId, s.menuApproved ? 1 : 0, s.proposedMenuModifications);
    }

    /**
     * Deletes a service along with its staff requests and menu proposals from the database.
     *
     * @param s the service to delete
     * @return {@code true} if the service row was deleted
     */
    public static boolean deleteService(Service s) {
        for (StaffRequest sr : new ArrayList<>(s.staffRequests)) {
            StaffRequest.deleteStaffRequest(sr);
        }
        s.staffRequests.clear();
        PersistenceManager.executeUpdate("DELETE FROM MenuProposals WHERE service_id = ?", s.id);
        String query = "DELETE FROM Services WHERE id = ?";
        return PersistenceManager.executeUpdate(query, s.id) > 0;
    }

    /**
     * Loads all services belonging to a specific event.
     *
     * @param eventId the parent event ID
     * @return list of persisted services for the event
     */
    public static ArrayList<Service> loadServicesForEvent(int eventId) {
        ArrayList<Service> services = new ArrayList<>();
        String query = "SELECT * FROM Services WHERE event_id = ?";
        PersistenceManager.executeQuery(query, new ResultHandler() {
            @Override
            public void handle(ResultSet rs) throws SQLException {
                services.add(mapRowToService(rs));
            }
        }, eventId);

        for (Service s : services) {
            loadServiceDetails(s);
        }
        return services;
    }

    /**
     * Loads a service by its database identifier.
     *
     * @param id the service ID
     * @return the loaded {@link Service}, or {@code null} if not found
     */
    public static Service loadById(int id) {
        String query = "SELECT * FROM Services WHERE id = ?";
        return loadServiceByQuery(query, id);
    }

    /**
     * Loads a service by its exact name.
     *
     * @param name the service name
     * @return the loaded {@link Service}, or {@code null} if not found
     */
    public static Service loadByName(String name) {
        String query = "SELECT * FROM Services WHERE name = ?";
        return loadServiceByQuery(query, name);
    }

    private static Service mapRowToService(ResultSet rs) throws SQLException {
        Service s = new Service();
        s.id = rs.getInt("id");
        s.name = rs.getString("name");
        String dStr = rs.getString("service_date");
        if (dStr != null && !dStr.isEmpty()) {
            try { s.date = Date.valueOf(dStr); } catch (Exception ignore) {}
        }
        String tsStr = rs.getString("time_start");
        if (tsStr != null && !tsStr.isEmpty()) {
            try { s.timeStart = Time.valueOf(tsStr); } catch (Exception ignore) {}
        }
        String teStr = rs.getString("time_end");
        if (teStr != null && !teStr.isEmpty()) {
            try { s.timeEnd = Time.valueOf(teStr); } catch (Exception ignore) {}
        }
        try { s.location = rs.getString("location"); } catch (SQLException ignore) {}
        try { s.expectedParticipants = rs.getInt("expected_participants"); } catch (SQLException ignore) {}
        try {
            State parsed = State.fromDbValue(rs.getString("state"));
            if (parsed != null) s.state = parsed;
        } catch (SQLException ignore) {}
        s.eventId = rs.getInt("event_id");

        int propMenuId = 0;
        try { propMenuId = rs.getInt("proposed_menu_id"); } catch (SQLException ignore) {}
        if (propMenuId > 0) {
            try { s.proposedMenu = Menu.load(propMenuId); } catch (Exception ignore) {}
        }

        int appMenuId = 0;
        try { appMenuId = rs.getInt("approved_menu_id"); } catch (SQLException ignore) {}
        if (appMenuId > 0) {
            try {
                s.menu = Menu.load(appMenuId);
                s.menuApproved = true;
            } catch (Exception ignore) {}
        }
        return s;
    }

    private static void loadServiceDetails(Service s) {
        if (s == null) return;
        try {
            s.staffRequests = StaffRequest.loadRequestsForService(s.id);
        } catch (Exception ignore) {}
        try {
            String propQuery = "SELECT * FROM MenuProposals WHERE service_id = ? ORDER BY id DESC LIMIT 1";
            PersistenceManager.executeQuery(propQuery, new ResultHandler() {
                @Override
                public void handle(ResultSet rs) throws SQLException {
                    s.menuApproved = rs.getInt("approved") == 1;
                    s.proposedMenuModifications = rs.getString("proposed_modifications");
                    int mId = rs.getInt("menu_id");
                    if (mId > 0 && s.menu == null && s.menuApproved) {
                        s.menu = Menu.load(mId);
                    }
                }
            }, s.id);
        } catch (Exception ignore) {}
    }

    private static Service loadServiceByQuery(String query, Object param) {
        final Service[] holder = new Service[1];
        PersistenceManager.executeQuery(query, new ResultHandler() {
            @Override
            public void handle(ResultSet rs) throws SQLException {
                holder[0] = mapRowToService(rs);
            }
        }, param);
        if (holder[0] != null) {
            loadServiceDetails(holder[0]);
        }
        return holder[0];
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Service other = (Service) obj;
        if (this.id > 0 && other.id > 0) {
            return this.id == other.id;
        }
        return this.eventId == other.eventId &&
               Objects.equals(this.name, other.name) &&
               Objects.equals(this.date, other.date);
    }

    @Override
    public int hashCode() {
        return (id > 0) ? Integer.hashCode(id) : Objects.hash(eventId, name, date);
    }

    @Override
    public String toString() {
        return "Service [id=" + id + ", name=" + name + ", date=" + date + "]";
    }
}