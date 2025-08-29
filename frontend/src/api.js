// Created a axios (link between frontend e backend)
// Responsable for get info from server
import axios from "axios";

export const api = axios.create({
    baseURL: "http://localhost:8080/api"
});
    