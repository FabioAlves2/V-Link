import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
    Box, Typography, Card, CardContent, CardActionArea,
    CardMedia, Chip, Button, CircularProgress, Alert
} from "@mui/material";
import { LocationOn, CalendarToday, EventAvailable, History, AccessTime } from "@mui/icons-material";
import { getVolunteerDashboard } from "../api/user";
import { resolveImageUrl } from "../utils/image";

function formatDate(dt) {
    if (!dt) return "—";
    return new Date(dt).toLocaleDateString("pt-PT", {
        day: "2-digit", month: "short", year: "numeric"
    });
}

function StatTile({ icon, label, value }) {
    return (
        <Box sx={{
            flex: "1 1 160px",
            backgroundColor: "#fff", borderRadius: "16px",
            boxShadow: "0 2px 16px rgba(27,67,50,0.07)",
            p: 2.5, display: "flex", alignItems: "center", gap: 1.5,
        }}>
            <Box sx={{
                width: 40, height: 40, borderRadius: "10px",
                backgroundColor: "#1B433312",
                display: "flex", alignItems: "center", justifyContent: "center",
            }}>
                {icon}
            </Box>
            <Box>
                <Typography sx={{ fontWeight: 700, fontSize: "1.3rem", color: "#1B4332" }}>
                    {value}
                </Typography>
                <Typography sx={{ fontSize: "0.8rem", color: "#4A5568" }}>
                    {label}
                </Typography>
            </Box>
        </Box>
    );
}

function EventCard({ event, navigate, chip }) {
    return (
        <Card
            sx={{
                borderRadius: "16px",
                boxShadow: "0 2px 16px rgba(27,67,50,0.07)",
                transition: "box-shadow 0.2s",
                "&:hover": { boxShadow: "0 8px 28px rgba(27,67,50,0.13)" },
            }}
        >
            <CardActionArea
                onClick={() => navigate(`/events/${event.id}`)}
                sx={{ display: "flex", alignItems: "stretch", p: 0, minWidth: 0 }}
            >
                <CardMedia
                    component="img"
                    image={resolveImageUrl(event.imageUrl, "https://images.unsplash.com/photo-1593113598332-cd288d649433?w=400&q=70")}
                    alt={event.title}
                    sx={{ width: { xs: 90, sm: 140 }, flexShrink: 0, objectFit: "cover" }}
                />
                <CardContent sx={{ flex: 1, p: { xs: 2, sm: 2.5 }, minWidth: 0 }}>
                    {chip && <Box sx={{ mb: 1 }}>{chip}</Box>}
                    <Typography sx={{
                        fontFamily: "'Playfair Display', serif",
                        fontWeight: 600, fontSize: "1rem",
                        color: "#1A1A1A", mb: 0.8,
                        overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap",
                    }}>
                        {event.title}
                    </Typography>
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
        </Card>
    );
}

export default function VolunteerDashboard() {
    const navigate = useNavigate();
    const [summary, setSummary] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        getVolunteerDashboard()
            .then(({ data }) => setSummary(data))
            .catch(() => setError("Erro ao carregar o painel."))
            .finally(() => setLoading(false));
    }, []);

    const hasAnyEvents = summary && (summary.upcomingEvents.length > 0 || summary.pastEvents.length > 0);

    return (
        <Box sx={{ maxWidth: 900, mx: "auto", py: 4, px: { xs: 2, md: 0 } }}>

            {/* Header */}
            <Box sx={{ mb: 4 }}>
                <Typography sx={{
                    fontFamily: "'Playfair Display', serif",
                    fontSize: { xs: "2rem", md: "2.4rem" },
                    fontWeight: 700, color: "#1A1A1A", mb: 0.5,
                }}>
                    O meu painel
                </Typography>
                <Typography sx={{ color: "#4A5568" }}>
                    O teu percurso como voluntário, num só lugar.
                </Typography>
            </Box>

            {error && (
                <Alert severity="error" sx={{ mb: 3, borderRadius: "10px" }}>{error}</Alert>
            )}

            {loading ? (
                <Box sx={{ display: "flex", justifyContent: "center", py: 10 }}>
                    <CircularProgress sx={{ color: "#1B4332" }} />
                </Box>
            ) : !summary ? null : !hasAnyEvents ? (
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
                <>
                    {/* Estatísticas */}
                    <Box sx={{ display: "flex", flexWrap: "wrap", gap: 2, mb: 4 }}>
                        <StatTile
                            icon={<EventAvailable sx={{ color: "#1B4332" }} />}
                            label="Próximos eventos"
                            value={summary.upcomingEvents.length}
                        />
                        <StatTile
                            icon={<History sx={{ color: "#1B4332" }} />}
                            label="Eventos passados"
                            value={summary.pastEvents.length}
                        />
                        <StatTile
                            icon={<AccessTime sx={{ color: "#1B4332" }} />}
                            label="Horas voluntariadas"
                            value={summary.totalHours}
                        />
                    </Box>

                    {/* Próximos eventos */}
                    <Typography sx={{
                        fontFamily: "'Playfair Display', serif",
                        fontWeight: 600, fontSize: "1.2rem",
                        color: "#1A1A1A", mb: 2,
                    }}>
                        Próximos eventos
                    </Typography>
                    {summary.upcomingEvents.length === 0 ? (
                        <Typography sx={{ color: "#4A5568", mb: 4 }}>
                            Não tens inscrições em eventos futuros.
                        </Typography>
                    ) : (
                        <Box sx={{ display: "flex", flexDirection: "column", gap: 2, mb: 4 }}>
                            {summary.upcomingEvents.map((event) => (
                                <EventCard key={event.id} event={event} navigate={navigate} />
                            ))}
                        </Box>
                    )}

                    {/* Eventos passados */}
                    <Typography sx={{
                        fontFamily: "'Playfair Display', serif",
                        fontWeight: 600, fontSize: "1.2rem",
                        color: "#1A1A1A", mb: 2,
                    }}>
                        Eventos passados
                    </Typography>
                    {summary.pastEvents.length === 0 ? (
                        <Typography sx={{ color: "#4A5568" }}>
                            Ainda não participaste em nenhum evento.
                        </Typography>
                    ) : (
                        <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
                            {summary.pastEvents.map(({ event, checkedIn }) => (
                                <EventCard
                                    key={event.id}
                                    event={event}
                                    navigate={navigate}
                                    chip={
                                        <Chip
                                            label={checkedIn ? "Participaste ✓" : "Sem confirmação de presença"}
                                            size="small"
                                            sx={{
                                                fontSize: "0.72rem", fontWeight: 600,
                                                backgroundColor: checkedIn ? "#52B78820" : "#6B728015",
                                                color: checkedIn ? "#16A34A" : "#6B7280",
                                            }}
                                        />
                                    }
                                />
                            ))}
                        </Box>
                    )}
                </>
            )}
        </Box>
    );
}
