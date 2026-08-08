import { API_BASE_URL } from "../api/axiosConfig";

export function resolveImageUrl(url, fallback) {
    if (!url) return fallback;
    return url.startsWith("/") ? `${API_BASE_URL}${url}` : url;
}
