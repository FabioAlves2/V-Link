import api from "./axiosConfig";

export const getEvents = (filters = {}) => {
    const params = {};
    if (filters.location) params.location = filters.location;
    if (filters.date) params.date = filters.date;
    if (filters.type) params.type = filters.type;
    return api.get("/events", { params });
};

export const getEvent = (id) =>
    api.get(`/events/${id}`);

export const createEvent = (eventData) =>
    api.post("/events", eventData);

export const updateEvent = (id, eventData) =>
    api.put(`/events/${id}`, eventData);