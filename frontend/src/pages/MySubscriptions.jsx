import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
    Box, Typography, Card, CardContent, CardActionArea,
    CardMedia, Chip, Button, CircularProgress, Alert
} from "@mui/material";
import { LocationOn, CalendarToday, BookmarkRemove } from "@mui/icons-material";
import api from "../api/axiosConfig";

function formatDate(dt) {
    if (!dt) return "—";
    return new Date(dt).toLocaleDateString("pt-PT", {
        day: "2-digit", month: "short", year: "numeric"
    });
}

function getStatus(event) {
    const now = new Date();
    if (!event.startDate) return "sem data";
    const start = new Date(event.startDate);
    const end = event.endDate ? new Date(event.endDate) : null;
    if (end && now > end) return "passado";
    if (now < start) return "próximo";
    return "a decorrer";
}

const STATUS_STYLE = {
    "próximo": { bg: "#1B433315", color: "#1B4332" },
    "a decorrer": { bg: "#52B78820", color: "#16A34A" },
    "passado": { bg: "#6B728015", color: "#6B7280" },
    "sem data": { bg: "#F59E0B15", color: "#B45309" },
};

export default function MySubscriptions() {
    const navigate = useNavigate();
    const [events, setEvents] = useState([]);
    const [loading, setLoading] = useState(true);
    const [removing, setRemoving] = useState(null);
    const [error, setError] = useState(null);

    const fetchSubscriptions = () => {
        setLoading(true);
        api.get("/subscriptions")
            .then(({ data }) => setEvents(data))
            .catch(() => setError("Erro ao carregar inscrições."))
            .finally(() => setLoading(false));
    };

    useEffect(() => { fetchSubscriptions(); }, []);

    const handleUnsubscribe = async (e, eventId) => {
        e.stopPropagation(); // não navegar para o evento
        setRemoving(eventId);
        try {
            await api.delete(`/subscriptions/${eventId}`);
            setEvents(prev => prev.filter(ev => ev.id !== eventId));
        } catch {
            setError("Erro ao cancelar inscrição.");
        } finally {
            setRemoving(null);
        }
    };

    return (
        <Box sx={{ maxWidth: 900, mx: "auto", py: 4, px: { xs: 2, md: 0 } }}>

            {/* Header */}
            <Box sx={{ mb: 4 }}>
                <Typography sx={{
                    fontFamily: "'Playfair Display', serif",
                    fontSize: { xs: "2rem", md: "2.4rem" },
                    fontWeight: 700, color: "#1A1A1A", mb: 0.5,
                }}>
                    As minhas inscrições
                </Typography>
                <Typography sx={{ color: "#4A5568" }}>
                    {events.length} evento{events.length !== 1 ? "s" : ""} inscrito{events.length !== 1 ? "s" : ""}
                </Typography>
            </Box>

            {error && (
                <Alert severity="error" sx={{ mb: 3, borderRadius: "10px" }}>{error}</Alert>
            )}

            {loading ? (
                <Box sx={{ display: "flex", justifyContent: "center", py: 10 }}>
                    <CircularProgress sx={{ color: "#1B4332" }} />
                </Box>
            ) : events.length === 0 ? (
                <Box sx={{
                    textAlign: "center", py: 10,
                    backgroundColor: "#fff", borderRadius: "20px",
                    boxShadow: "0 2px 16px rgba(27,67,50,0.07)",
                }}>
                    <Typography sx={{ fontSize: "3rem", mb: 2 }}>🌱</Typography>
                    <Typography sx={{
                        fontFamily: "'Playfair Display', serif",
                        fontSize: "1.4rem", fontWeight: 600,
                        color: "#1A1A1A", mb: 1,
                    }}>
                        Ainda não te inscreveste em nenhum evento
                    </Typography>
                    <Typography sx={{ color: "#4A5568", mb: 3 }}>
                        Explora os eventos disponíveis e começa a contribuir.
                    </Typography>
                    <Button
                        variant="contained"
                        onClick={() => navigate("/events")}
                        sx={{ backgroundColor: "#1B4332", "&:hover": { backgroundColor: "#0A2318" } }}
                    >
                        Ver eventos
                    </Button>
                </Box>
            ) : (
                <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
                    {events.map((event) => {
                        const status = getStatus(event);
                        const style = STATUS_STYLE[status];

                        return (
                            <Card
                                key={event.id}
                                sx={{
                                    borderRadius: "16px",
                                    boxShadow: "0 2px 16px rgba(27,67,50,0.07)",
                                    transition: "box-shadow 0.2s",
                                    "&:hover": { boxShadow: "0 8px 28px rgba(27,67,50,0.13)" },
                                    opacity: status === "passado" ? 0.75 : 1,
                                }}
                            >
                                <Box sx={{ display: "flex", alignItems: "stretch" }}>
                                    <CardActionArea
                                        onClick={() => navigate(`/events/${event.id}`)}
                                        sx={{ display: "flex", alignItems: "stretch", flex: 1, p: 0, minWidth: 0 }}
                                    >
                                        {/* Imagem lateral */}
                                        <CardMedia
                                            component="img"
                                            image={event.imageUrl || "https://images.unsplash.com/photo-1593113598332-cd288d649433?w=400&q=70"}
                                            alt={event.title}
                                            sx={{
                                                width: { xs: 90, sm: 140 },
                                                flexShrink: 0,
                                                objectFit: "cover",
                                            }}
                                        />

                                        {/* Conteúdo */}
                                        <CardContent sx={{ flex: 1, p: { xs: 2, sm: 2.5 }, minWidth: 0 }}>
                                            {/* Status */}
                                            <Chip
                                                label={status}
                                                size="small"
                                                sx={{
                                                    mb: 1, fontSize: "0.72rem", fontWeight: 600,
                                                    backgroundColor: style.bg,
                                                    color: style.color,
                                                    textTransform: "capitalize",
                                                }}
                                            />
                                            {/* Título */}
                                            <Typography sx={{
                                                fontFamily: "'Playfair Display', serif",
                                                fontWeight: 600, fontSize: "1rem",
                                                color: "#1A1A1A", mb: 0.8,
                                                overflow: "hidden", textOverflow: "ellipsis",
                                                whiteSpace: "nowrap",
                                            }}>
                                                {event.title}
                                            </Typography>
                                            {/* Meta */}
                                            <Box sx={{ display: "flex", flexWrap: "wrap", gap: 2 }}>
                                                <Box sx={{ display: "flex", alignItems: "center", gap: 0.5 }}>
                                                    <LocationOn sx={{ fontSize: 14, color: "#52B788" }} />
                                                    <Typography sx={{ fontSize: "0.82rem", color: "#4A5568" }}>
                                                        {event.location}
                                                    </Typography>
                                                </Box>
                                                <Box sx={{ display: "flex", alignItems: "center", gap: 0.5 }}>
                                                    <CalendarToday sx={{ fontSize: 14, color: "#52B788" }} />
                                                    <Typography sx={{ fontSize: "0.82rem", color: "#4A5568" }}>
                                                        {formatDate(event.startDate)}
                                                    </Typography>
                                                </Box>
                                            </Box>
                                        </CardContent>
                                    </CardActionArea>

                                    {/* Botão cancelar — fora da CardActionArea para não aninhar <button> dentro de <button> */}
                                    {status !== "passado" && (
                                        <Box sx={{ display: "flex", alignItems: "center", px: { xs: 2, sm: 2.5 } }}>
                                            <Button
                                                variant="outlined" size="small"
                                                startIcon={removing === event.id
                                                    ? <CircularProgress size={12} />
                                                    : <BookmarkRemove />
                                                }
                                                disabled={removing === event.id}
                                                onClick={(e) => handleUnsubscribe(e, event.id)}
                                                sx={{
                                                    color: "#E53E3E", borderColor: "#E53E3E40",
                                                    fontSize: "0.8rem", whiteSpace: "nowrap",
                                                    "&:hover": {
                                                        backgroundColor: "#E53E3E10",
                                                        borderColor: "#E53E3E",
                                                    },
                                                }}
                                            >
                                                Cancelar
                                            </Button>
                                        </Box>
                                    )}
                                </Box>
                            </Card>
                        );
                    })}
                </Box>
            )}
        </Box>
    );
}