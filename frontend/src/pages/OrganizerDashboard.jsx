import { useEffect, useState } from "react";
import { Link as RouterLink, useNavigate } from "react-router-dom";
import {
    Box, Typography, Card, CardContent, CardActionArea,
    CardMedia, Chip, Button, CircularProgress, Alert
} from "@mui/material";
import { Group, Edit, HighlightOff, AddCircleOutline, DeleteOutline } from "@mui/icons-material";
import { getMyEvents, updateEvent, deleteEvent } from "../api/event";
import { resolveImageUrl } from "../utils/image";

const FALLBACK_IMAGE = "https://images.unsplash.com/photo-1593113598332-cd288d649433?w=400&q=70";

const STATUS_LABEL = { DRAFT: "Rascunho", PUBLISHED: "Publicado", CLOSED: "Encerrado" };
const STATUS_STYLE = {
    DRAFT: { bg: "#D4A85320", color: "#B4842F" },
    PUBLISHED: { bg: "#52B78820", color: "#16A34A" },
    CLOSED: { bg: "#6B728015", color: "#6B7280" },
};

function formatDate(dt) {
    if (!dt) return "—";
    return new Date(dt).toLocaleDateString("pt-PT", {
        day: "2-digit", month: "short", year: "numeric"
    });
}

export default function OrganizerDashboard() {
    const navigate = useNavigate();
    const [events, setEvents] = useState([]);
    const [loading, setLoading] = useState(true);
    const [closing, setClosing] = useState(null);
    const [deleting, setDeleting] = useState(null);
    const [error, setError] = useState(null);

    useEffect(() => {
        getMyEvents()
            .then(({ data }) => setEvents(data))
            .catch(() => setError("Erro ao carregar os teus eventos."))
            .finally(() => setLoading(false));
    }, []);

    const hasStarted = (event) => new Date(event.startDate) <= new Date();
    // Encerrado não é o mesmo que terminado (ver Events em CLAUDE.md) — um evento encerrado
    // antes do fim ainda pode ter detalhes a corrigir. Só bloqueia edição depois do endDate
    // ter mesmo passado, independentemente do status.
    const hasEnded = (event) => event.endDate ? new Date(event.endDate) < new Date() : false;

    const handleClose = async (e, event) => {
        e.stopPropagation();
        if (!window.confirm(`Encerrar "${event.title}"? Os inscritos serão notificados.`)) return;
        setClosing(event.id);
        try {
            const { data } = await updateEvent(event.id, { ...event, status: "CLOSED" });
            setEvents((prev) => prev.map((ev) => (ev.id === event.id ? data : ev)));
        } catch {
            setError("Erro ao encerrar o evento.");
        } finally {
            setClosing(null);
        }
    };

    const handleDelete = async (e, event, confirmMessage) => {
        e.stopPropagation();
        if (!window.confirm(confirmMessage)) return;
        setDeleting(event.id);
        try {
            await deleteEvent(event.id);
            setEvents((prev) => prev.filter((ev) => ev.id !== event.id));
        } catch (err) {
            setError(err.response?.data?.error || "Erro ao eliminar o evento.");
        } finally {
            setDeleting(null);
        }
    };

    return (
        <Box sx={{ maxWidth: 900, mx: "auto", py: 4, px: { xs: 2, md: 0 } }}>
            <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end", mb: 4, flexWrap: "wrap", gap: 2 }}>
                <Box>
                    <Typography sx={{
                        fontFamily: "'Playfair Display', serif",
                        fontSize: { xs: "2rem", md: "2.4rem" },
                        fontWeight: 700, color: "#1A1A1A", mb: 0.5,
                    }}>
                        Painel do organizador
                    </Typography>
                    <Typography sx={{ color: "#4A5568" }}>
                        {events.length} evento{events.length !== 1 ? "s" : ""} criado{events.length !== 1 ? "s" : ""}
                    </Typography>
                </Box>
                <Button
                    variant="contained"
                    startIcon={<AddCircleOutline />}
                    onClick={() => navigate("/new")}
                    sx={{ backgroundColor: "#1B4332", "&:hover": { backgroundColor: "#0A2318" } }}
                >
                    Criar evento
                </Button>
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
                    <Typography sx={{ fontSize: "3rem", mb: 2 }}>📋</Typography>
                    <Typography sx={{
                        fontFamily: "'Playfair Display', serif",
                        fontSize: "1.4rem", fontWeight: 600,
                        color: "#1A1A1A", mb: 1,
                    }}>
                        Ainda não criaste nenhum evento
                    </Typography>
                    <Typography sx={{ color: "#4A5568", mb: 3 }}>
                        Cria o teu primeiro evento e começa a reunir voluntários.
                    </Typography>
                    <Button
                        variant="contained"
                        onClick={() => navigate("/new")}
                        sx={{ backgroundColor: "#1B4332", "&:hover": { backgroundColor: "#0A2318" } }}
                    >
                        Criar evento
                    </Button>
                </Box>
            ) : (
                <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
                    {events.map((event) => {
                        const style = STATUS_STYLE[event.status];

                        return (
                            <Card key={event.id} sx={{
                                borderRadius: "16px",
                                boxShadow: "0 2px 16px rgba(27,67,50,0.07)",
                                transition: "box-shadow 0.2s",
                                "&:hover": { boxShadow: "0 8px 28px rgba(27,67,50,0.13)" },
                            }}>
                                <Box sx={{ display: "flex", alignItems: "stretch", flexWrap: "wrap" }}>
                                    <CardActionArea
                                        onClick={() => navigate(`/events/${event.id}/edit`)}
                                        disabled={hasEnded(event)}
                                        sx={{ display: "flex", alignItems: "stretch", flex: 1, p: 0, minWidth: 0 }}
                                    >
                                        <CardMedia
                                            component="img"
                                            image={resolveImageUrl(event.imageUrl, FALLBACK_IMAGE)}
                                            alt={event.title}
                                            sx={{ width: { xs: 90, sm: 140 }, flexShrink: 0, objectFit: "cover" }}
                                        />
                                        <CardContent sx={{ flex: 1, p: { xs: 2, sm: 2.5 }, minWidth: 0 }}>
                                            <Chip
                                                label={STATUS_LABEL[event.status] || event.status}
                                                size="small"
                                                sx={{
                                                    mb: 1, fontSize: "0.72rem", fontWeight: 600,
                                                    backgroundColor: style?.bg, color: style?.color,
                                                }}
                                            />
                                            <Typography sx={{
                                                fontFamily: "'Playfair Display', serif",
                                                fontWeight: 600, fontSize: "1rem",
                                                color: "#1A1A1A", mb: 0.8,
                                                overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap",
                                            }}>
                                                {event.title}
                                            </Typography>
                                            <Box sx={{ display: "flex", flexWrap: "wrap", gap: 2 }}>
                                                <Typography sx={{ fontSize: "0.82rem", color: "#4A5568" }}>
                                                    {formatDate(event.startDate)}
                                                </Typography>
                                                <Typography sx={{ fontSize: "0.82rem", color: "#4A5568" }}>
                                                    {event.subscriberCount ?? 0} / {event.capacity} inscritos
                                                </Typography>
                                            </Box>
                                        </CardContent>
                                    </CardActionArea>

                                    {/* Ações — fora da CardActionArea para não aninhar <button> dentro de <button> */}
                                    <Box sx={{ display: "flex", alignItems: "center", gap: 1, px: { xs: 2, sm: 2.5 }, py: 1.5, flexWrap: "wrap" }}>
                                        <Button
                                            component={RouterLink} to={`/events/${event.id}/subscribers`}
                                            variant="outlined" size="small" startIcon={<Group />}
                                            sx={{ color: "#1B4332", borderColor: "#1B433240", whiteSpace: "nowrap" }}
                                        >
                                            Ver inscritos
                                        </Button>
                                        {!hasEnded(event) && (
                                            <Button
                                                component={RouterLink} to={`/events/${event.id}/edit`}
                                                variant="outlined" size="small" startIcon={<Edit />}
                                                sx={{ color: "#1B4332", borderColor: "#1B433240", whiteSpace: "nowrap" }}
                                            >
                                                Editar
                                            </Button>
                                        )}
                                        {event.status === "DRAFT" && (
                                            <Button
                                                variant="outlined" size="small"
                                                startIcon={deleting === event.id ? <CircularProgress size={12} /> : <DeleteOutline />}
                                                disabled={deleting === event.id}
                                                onClick={(e) => handleDelete(e, event,
                                                    `Eliminar o rascunho "${event.title}"? Esta ação não pode ser desfeita.`)}
                                                sx={{
                                                    color: "#E53E3E", borderColor: "#E53E3E40", whiteSpace: "nowrap",
                                                    "&:hover": { backgroundColor: "#E53E3E10", borderColor: "#E53E3E" },
                                                }}
                                            >
                                                Eliminar
                                            </Button>
                                        )}
                                        {event.status === "PUBLISHED" && !hasStarted(event) && (
                                            <Button
                                                variant="outlined" size="small"
                                                startIcon={deleting === event.id ? <CircularProgress size={12} /> : <DeleteOutline />}
                                                disabled={deleting === event.id}
                                                onClick={(e) => handleDelete(e, event,
                                                    `Cancelar "${event.title}"? Esta ação é permanente — o evento e as inscrições serão eliminados, e os inscritos serão notificados.`)}
                                                sx={{
                                                    color: "#E53E3E", borderColor: "#E53E3E40", whiteSpace: "nowrap",
                                                    "&:hover": { backgroundColor: "#E53E3E10", borderColor: "#E53E3E" },
                                                }}
                                            >
                                                Cancelar
                                            </Button>
                                        )}
                                        {event.status === "PUBLISHED" && hasStarted(event) && (
                                            <Button
                                                variant="outlined" size="small"
                                                startIcon={closing === event.id ? <CircularProgress size={12} /> : <HighlightOff />}
                                                disabled={closing === event.id}
                                                onClick={(e) => handleClose(e, event)}
                                                sx={{
                                                    color: "#E53E3E", borderColor: "#E53E3E40", whiteSpace: "nowrap",
                                                    "&:hover": { backgroundColor: "#E53E3E10", borderColor: "#E53E3E" },
                                                }}
                                            >
                                                Encerrar
                                            </Button>
                                        )}
                                    </Box>
                                </Box>
                            </Card>
                        );
                    })}
                </Box>
            )}
        </Box>
    );
}
