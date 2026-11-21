package catering.persistence;

import catering.businesslogic.event.Event;
import catering.businesslogic.event.EventInstance;
import catering.businesslogic.event.EventReceiver;
import catering.businesslogic.event.Service;
import catering.businesslogic.event.StaffRequest;

/**
 * Persistence observer implementing {@link EventReceiver}.
 * Translates domain notifications from {@link catering.businesslogic.event.EventManager}
 * into SQLite persistence operations via the entity-level static methods.
 */
public class EventPersistence implements EventReceiver {

    @Override
    public void updateEventCreated(Event e) {
        Event.saveNewEvent(e);
    }

    @Override
    public void updateEventUpdated(Event e) {
        Event.updateEvent(e);
        if (e.isRecurring() && e.getInstances() != null) {
            for (EventInstance inst : e.getInstances()) {
                if (inst.getId() == 0) {
                    inst.setEventId(e.getId());
                    EventInstance.saveNewInstance(inst);
                } else {
                    EventInstance.updateInstance(inst);
                }
            }
        }
    }

    @Override
    public void updateEventDeleted(Event e) {
        Event.deleteEvent(e);
    }

    @Override
    public void updateEventInstanceUpdated(EventInstance ei) {
        EventInstance.updateInstance(ei);
    }

    @Override
    public void updateEventInstanceDeleted(EventInstance ei) {
        EventInstance.deleteInstance(ei);
    }

    @Override
    public void updateServiceCreated(Event e, Service s) {
        s.setEventId(e.getId());
        Service.saveNewService(s);
    }

    @Override
    public void updateServiceUpdated(Service s) {
        Service.updateService(s);
        if (s.isMenuApproved() || s.getProposedMenuModifications() != null) {
            Service.saveMenuProposal(s);
        }
    }

    @Override
    public void updateServiceDeleted(Event e, Service s) {
        Service.deleteService(s);
    }

    @Override
    public void updateStaffRequested(Service s, StaffRequest sr) {
        sr.setServiceId(s.getId());
        StaffRequest.saveNewStaffRequest(sr);
    }

    @Override
    public void updateStaffRequestUpdated(Service s, StaffRequest sr) {
        StaffRequest.updateStaffRequest(sr);
    }

    @Override
    public void updateStaffRequestDeleted(Service s, StaffRequest sr) {
        StaffRequest.deleteStaffRequest(sr);
    }
}