import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
    Box, Typography, Card, CardContent, CardActionArea,
    CardMedia, Button, CircularProgress, Alert, IconButton
} from "@mui/material";
import { LocationOn, CalendarToday, Favorite } from "@mui/icons-material";
import { getFavorites, unfavoriteEvent } from "../api/user";
import { resolveImageUrl } from "../utils/image";

function formatDate(dt) {
    if (!dt) return "—";
    return new Date(dt).toLocaleDateString("pt-PT", {
        day: "2-digit", month: "short", year: "numeric"
    });
}

export default function MyFavorites() {
    const navigate = useNavigate();
    const [events, setEvents] = useState([]);
    const [loading, setLoading] = useState(true);
    const [removing, setRemoving] = useState(null);
    const [error, setError] = useState(null);

    const fetchFavorites = () => {
        setLoading(true);
        getFavorites()
            .then(({ data }) => setEvents(data))
            .catch(() => setError("Erro ao carregar favoritos."))
            .finally(() => setLoading(false));
    };

    useEffect(() => { fetchFavorites(); }, []);

    const handleUnfavorite = async (e, eventId) => {
        e.stopPropagation(); // não navegar para o evento
        setRemoving(eventId);
        try {
            await unfavoriteEvent(eventId);
            setEvents(prev => prev.filter(ev => ev.id !== eventId));
        } catch (err) {
            setError(err.response?.data?.error || "Erro ao remover favorito.");
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
                    Os meus favoritos
                </Typography>
                <Typography sx={{ color: "#4A5568" }}>
                    {events.length} evento{events.length !== 1 ? "s" : ""} favoritado{events.length !== 1 ? "s" : ""}
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
                    <Typography sx={{ fontSize: "3rem", mb: 2 }}>🤍</Typography>
                    <Typography sx={{
                        fontFamily: "'Playfair Display', serif",
                        fontSize: "1.4rem", fontWeight: 600,
                        color: "#1A1A1A", mb: 1,
                    }}>
                        Ainda não tens eventos favoritos
                    </Typography>
                    <Typography sx={{ color: "#4A5568", mb: 3 }}>
                        Explora os eventos disponíveis e guarda os que te interessam.
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
                    {events.map((event) => (
                        <Card
                            key={event.id}
                            sx={{
                                borderRadius: "16px",
                                boxShadow: "0 2px 16px rgba(27,67,50,0.07)",
                                transition: "box-shadow 0.2s",
                                "&:hover": { boxShadow: "0 8px 28px rgba(27,67,50,0.13)" },
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
                                        image={resolveImageUrl(event.imageUrl, "https://images.unsplash.com/photo-1593113598332-cd288d649433?w=400&q=70")}
                                        alt={event.title}
                                        sx={{
                                            width: { xs: 90, sm: 140 },
                                            flexShrink: 0,
                                            objectFit: "cover",
                                        }}
                                    />

                                    {/* Conteúdo */}
                                    <CardContent sx={{ flex: 1, p: { xs: 2, sm: 2.5 }, minWidth: 0 }}>
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

                                {/* Botão remover — fora da CardActionArea para não aninhar <button> dentro de <button> */}
                                <Box sx={{ display: "flex", alignItems: "center", px: { xs: 2, sm: 2.5 } }}>
                                    <IconButton
                                        disabled={removing === event.id}
                                        onClick={(e) => handleUnfavorite(e, event.id)}
                                        aria-label="Remover dos favoritos"
                                        sx={{
                                            color: "#E53E3E",
                                            "&:hover": { backgroundColor: "#E53E3E10" },
                                        }}
                                    >
                                        {removing === event.id ? <CircularProgress size={18} /> : <Favorite />}
                                    </IconButton>
                                </Box>
                            </Box>
                        </Card>
                    ))}
                </Box>
            )}
        </Box>
    );
}
