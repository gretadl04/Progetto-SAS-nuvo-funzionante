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

public class Service {
    private int id;
    private String name;
    private Date date;
    private Time timeStart;
    private Time timeEnd;
    private String location;
    private int expectedParticipants;
    private String state;
    private int eventId;
    private Menu proposedMenu;
    private Menu menu;

    // Estensioni per Gestire gli Eventi
    private boolean menuApproved;
    private String proposedMenuModifications;
    private ArrayList<StaffRequest> staffRequests;

    public Service() {
        this.staffRequests = new ArrayList<>();
        this.menuApproved = false;
        this.state = "attivo";
    }

    public Service(String name) {
        this();
        this.name = name;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Date getDate() { return date; }
    public void setDate(Date date) { this.date = date; }
    public Time getTimeStart() { return timeStart; }
    public void setTimeStart(Time timeStart) { this.timeStart = timeStart; }
    public Time getTimeEnd() { return timeEnd; }
    public void setTimeEnd(Time timeEnd) { this.timeEnd = timeEnd; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public int getExpectedParticipants() { return expectedParticipants; }
    public void setExpectedParticipants(int expectedParticipants) { this.expectedParticipants = expectedParticipants; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public int getEventId() { return eventId; }
    public void setEventId(int eventId) { this.eventId = eventId; }

    public Menu getProposedMenu() { return proposedMenu; }
    public int getProposedMenuId() { return proposedMenu != null ? proposedMenu.getId() : 0; }
    public void setProposedMenu(Menu proposedMenu) { this.proposedMenu = proposedMenu; }

    public Menu getMenu() { return menu; }
    public int getMenuId() { return menu != null ? menu.getId() : 0; }
    public void setMenu(Menu menu) { this.menu = menu; }

    public boolean isMenuApproved() { return menuApproved; }
    public void setMenuApproved(boolean menuApproved) { this.menuApproved = menuApproved; }
    public String getProposedMenuModifications() { return proposedMenuModifications; }
    public void setProposedMenuModifications(String mods) { this.proposedMenuModifications = mods; }
    public ArrayList<StaffRequest> getStaffRequests() { return staffRequests; }

    public void addStaffRequest(StaffRequest sr) {
        if (this.staffRequests == null) this.staffRequests = new ArrayList<>();
        if (!this.staffRequests.contains(sr)) {
            this.staffRequests.add(sr);
        }
    }

    public boolean removeStaffRequest(StaffRequest sr) {
        if (this.staffRequests == null) return false;
        return this.staffRequests.remove(sr);
    }

    public void proposeMenu(Menu m, String modifications) {
        this.proposedMenu = m;
        this.menuApproved = false;
        this.proposedMenuModifications = modifications;
    }

    public void approveMenu(Menu m, String modifications) {
        this.menu = m;
        if (this.proposedMenu == null) {
            this.proposedMenu = m;
        }
        this.menuApproved = true;
        this.proposedMenuModifications = modifications;
    }

    public void saveNewService() {
        String query = "INSERT INTO Services (event_id, name, service_date, time_start, time_end, expected_participants, location, proposed_menu_id, approved_menu_id, state) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String dStr = (date != null) ? date.toString() : null;
        String tStart = (timeStart != null) ? timeStart.toString() : null;
        String tEnd = (timeEnd != null) ? timeEnd.toString() : null;

        PersistenceManager.executeUpdate(query, eventId, name, dStr, tStart, tEnd, expectedParticipants, location, getProposedMenuId(), getMenuId(), state);
        this.id = PersistenceManager.getLastId();
    }

    public void updateService() {
        String query = "UPDATE Services SET name = ?, service_date = ?, time_start = ?, time_end = ?, expected_participants = ?, location = ?, proposed_menu_id = ?, approved_menu_id = ?, state = ? WHERE id = ?";
        String dStr = (date != null) ? date.toString() : null;
        String tStart = (timeStart != null) ? timeStart.toString() : null;
        String tEnd = (timeEnd != null) ? timeEnd.toString() : null;

        PersistenceManager.executeUpdate(query, name, dStr, tStart, tEnd, expectedParticipants, location, getProposedMenuId(), getMenuId(), state, id);
    }

    public void saveMenuProposal() {
        int targetMenuId = (menu != null) ? getMenuId() : getProposedMenuId();
        String deleteOld = "DELETE FROM MenuProposals WHERE service_id = ?";
        PersistenceManager.executeUpdate(deleteOld, id);
        String query = "INSERT INTO MenuProposals (service_id, menu_id, approved, proposed_modifications) VALUES (?, ?, ?, ?)";
        PersistenceManager.executeUpdate(query, id, targetMenuId, menuApproved ? 1 : 0, proposedMenuModifications);
    }

    public boolean deleteService() {
        for (StaffRequest sr : staffRequests) {
            sr.deleteStaffRequest();
        }
        staffRequests.clear();
        PersistenceManager.executeUpdate("DELETE FROM MenuProposals WHERE service_id = ?", id);
        String query = "DELETE FROM Services WHERE id = ?";
        return PersistenceManager.executeUpdate(query, id) > 0;
    }

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

    public static Service loadById(int id) {
        String query = "SELECT * FROM Services WHERE id = ?";
        return loadServiceByQuery(query, id);
    }

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
            String st = rs.getString("state");
            if (st != null && !st.isEmpty()) s.state = st;
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