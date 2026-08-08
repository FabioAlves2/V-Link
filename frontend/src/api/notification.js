import api from "./axiosConfig";

export const getNotifications = () =>
    api.get("/notifications");

export const getUnreadCount = () =>
    api.get("/notifications/unread-count");

export const markNotificationRead = (id) =>
    api.put(`/notifications/${id}/read`);

export const markAllNotificationsRead = () =>
    api.put("/notifications/read-all");
