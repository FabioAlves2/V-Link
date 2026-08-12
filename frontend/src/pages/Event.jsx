import { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import {
    Box, Typography, Button, Chip, LinearProgress,
    Skeleton, Alert, IconButton
} from "@mui/material";
import {
    LocationOn, CalendarToday, People, ArrowBack,
    BookmarkAdd, BookmarkAdded, FavoriteBorder, Favorite
} from "@mui/icons-material";
import { isSubscribed, subscribe, unsubscribe, isFavorited, favoriteEvent, unfavoriteEvent } from "../api/user";
import { getEvent } from "../api/event";
import { resolveImageUrl } from "../utils/image";
import { useAuth } from "../context/authContext";

const TYPE_LABELS = {
    LIMPEZA: "🧹 Limpeza", DOACAO: "🎁 Doação",
    EDUCACAO: "📚 Educação", AMBIENTE: "🌿 Ambiente",
    SOCIAL: "🤝 Social", OUTRO: "💡 Outro",
};

function formatDateTime(dt) {
    if (!dt) return "—";
    return new Date(dt).toLocaleString("pt-PT", {
        day: "2-digit", month: "long", year: "numeric",
        hour: "2-digit", minute: "2-digit"
    });
}

export default function Event() {
    const { id } = useParams();
    const navigate = useNavigate();
    const { token } = useAuth();
    const [event, setEvent] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [subscribed, setSubscribed] = useState(false);
    const [subLoading, setSubLoading] = useState(false);
    const [subError, setSubError] = useState(null);
    const [favorited, setFavorited] = useState(false);
    const [favLoading, setFavLoading] = useState(false);
    const [favError, setFavError] = useState(null);

    //Carrega evento (público) e, só com sessão iniciada, verifica se está inscrito/favoritou —
    //sem token estes pedidos dão 401 e, antes da correção no axiosConfig.js, forçavam um
    //refresh destinado a falhar seguido de redirect duro para /login.
    useEffect(() => {
        if (!id) return;
        let cancelled = false;
        // Sem isto, navegar diretamente de um evento para outro (sem desmontar o componente)
        // mostrava por um instante o estado "Inscrito ✓"/coração preenchido do evento ANTERIOR
        // sobre o novo, até isSubscribed/isFavorited(id novo) responderem.
        setSubscribed(false);
        setFavorited(false);

        getEvent(id)
            .then(({ data }) => { if (!cancelled) setEvent(data); })
            .catch(() => { if (!cancelled) setError("Evento não encontrado."); })
            .finally(() => { if (!cancelled) setLoading(false); });

        if (token) {
            isSubscribed(id)
                .then(({ data }) => { if (!cancelled) setSubscribed(data.subscribed); })
                .catch(() => { });

            isFavorited(id)
                .then(({ data }) => { if (!cancelled) setFavorited(data.favorited); })
                .catch(() => { });
        }

        return () => { cancelled = true; };
    }, [id, token]);


    //Subscrever/Cancelar inscrição
    const handleSubscribe = async () => {
        setSubLoading(true);
        setSubError(null);
        try {
            if (subscribed) {
                await unsubscribe(id);
                setSubscribed(false);
                setEvent(ev => ({ ...ev, subscriberCount: Math.max((ev.subscriberCount ?? 0) - 1, 0) }));
            } else {
                await subscribe(id);
                setSubscribed(true);
                setEvent(ev => ({ ...ev, subscriberCount: (ev.subscriberCount ?? 0) + 1 }));
            }
        } catch (err) {
            setSubError(err.response?.data?.error || "Não foi possível concluir a operação. Tenta novamente.");
        } finally {
            setSubLoading(false);
        }
    };

    //Favoritar/Desfavoritar — bookmark sem compromisso, independente da inscrição
    const handleToggleFavorite = async () => {
        if (!token) { navigate("/login"); return; }
        setFavLoading(true);
        setFavError(null);
        try {
            if (favorited) {
                await unfavoriteEvent(id);
                setFavorited(false);
            } else {
                await favoriteEvent(id);
                setFavorited(true);
            }
        } catch (err) {
            setFavError(err.response?.data?.error || "Não foi possível concluir a operação. Tenta novamente.");
        } finally {
            setFavLoading(false);
        }
    };

    if (loading) return (
        <Box sx={{ maxWidth: 800, mx: "auto", py: 4 }}>
            <Skeleton variant="rectangular" height={360} sx={{ borderRadius: "20px", mb: 3 }} />
            <Skeleton height={40} sx={{ mb: 1 }} />
            <Skeleton height={24} width="60%" />
        </Box>
    );

    if (error) return (
        <Box sx={{ maxWidth: 800, mx: "auto", py: 4 }}>
            <Alert severity="error">{error}</Alert>
        </Box>
    );

    const registered = event.subscriberCount ?? 0;
    const capacityPct = Math.min((registered / event.capacity) * 100, 100);
    // Um evento PUBLISHED cujo endDate já passou continua PUBLISHED até o organizador o
    // "Encerrar" manualmente (ver EventRepository.findByFilters) — por isso "a decorrer" exige
    // as duas condições, não só o status. O backend já rejeita subscribe/unsubscribe nestes
    // casos (ver SubscriptionController); isto evita mostrar um botão que o servidor vai recusar.
    const isPast = event.endDate ? new Date(event.endDate) < new Date() : false;
    const canModifySubscription = event.status === "PUBLISHED" && !isPast;
    const unavailableReason = event.status === "CLOSED"
        ? "Este evento já foi encerrado."
        : isPast
            ? "Este evento já terminou."
            : "Este evento ainda não está disponível para inscrições.";

    return (
        <Box sx={{ maxWidth: 800, mx: "auto", py: 4, px: { xs: 2, md: 0 } }}>

            {/* Back */}
            <Button
                startIcon={<ArrowBack />} onClick={() => navigate("/events")}
                sx={{ color: "#1B4332", mb: 3, fontWeight: 500 }}
            >
                Voltar aos eventos
            </Button>

            {/* Imagem */}
            <Box sx={{
                position: "relative",
                borderRadius: "20px", overflow: "hidden",
                height: { xs: 220, md: 380 }, mb: 4,
                boxShadow: "0 8px 32px rgba(27,67,50,0.15)",
            }}>
                <img
                    src={resolveImageUrl(event.imageUrl, "https://images.unsplash.com/photo-1593113598332-cd288d649433?w=900&q=80")}
                    alt={event.title}
                    style={{ width: "100%", height: "100%", objectFit: "cover" }}
                />
                <IconButton
                    onClick={handleToggleFavorite}
                    disabled={favLoading}
                    aria-label={favorited ? "Remover dos favoritos" : "Adicionar aos favoritos"}
                    sx={{
                        position: "absolute", top: 12, right: 12,
                        backgroundColor: "rgba(255,255,255,0.9)",
                        "&:hover": { backgroundColor: "#fff" },
                    }}
                >
                    {favorited ? <Favorite sx={{ color: "#E53E3E" }} /> : <FavoriteBorder sx={{ color: "#1B4332" }} />}
                </IconButton>
            </Box>

            {favError && <Alert severity="error" sx={{ mb: 3, borderRadius: "10px" }}>{favError}</Alert>}

            {/* Conteúdo */}
            <Box sx={{
                backgroundColor: "#fff", borderRadius: "20px",
                p: { xs: 3, md: 4 },
                boxShadow: "0 2px 16px rgba(27,67,50,0.07)",
            }}>
                {/* Tipo */}
                {event.type && (
                    <Chip
                        label={TYPE_LABELS[event.type] || event.type}
                        size="small"
                        sx={{
                            mb: 2, fontWeight: 600,
                            backgroundColor: "#1B433215",
                            color: "#1B4332",
                        }}
                    />
                )}

                {/* Título */}
                <Typography sx={{
                    fontFamily: "'Playfair Display', serif",
                    fontSize: { xs: "1.8rem", md: "2.2rem" },
                    fontWeight: 700, color: "#1A1A1A", mb: 3, lineHeight: 1.2,
                }}>
                    {event.title}
                </Typography>

                {/* Metainfo */}
                <Box sx={{ display: "flex", flexDirection: "column", gap: 1.5, mb: 3 }}>
                    <Box sx={{ display: "flex", alignItems: "center", gap: 1.5 }}>
                        <Box sx={{
                            width: 36, height: 36, borderRadius: "10px",
                            backgroundColor: "#1B433312",
                            display: "flex", alignItems: "center", justifyContent: "center",
                        }}>
                            <LocationOn sx={{ color: "#1B4332", fontSize: 18 }} />
                        </Box>
                        <Typography sx={{ color: "#1A1A1A", fontWeight: 500 }}>
                            {event.location}
                        </Typography>
                    </Box>
                    <Box sx={{ display: "flex", alignItems: "center", gap: 1.5 }}>
                        <Box sx={{
                            width: 36, height: 36, borderRadius: "10px",
                            backgroundColor: "#1B433312",
                            display: "flex", alignItems: "center", justifyContent: "center",
                        }}>
                            <CalendarToday sx={{ color: "#1B4332", fontSize: 18 }} />
                        </Box>
                        <Box>
                            <Typography sx={{ color: "#1A1A1A", fontWeight: 500 }}>
                                {formatDateTime(event.startDate)}
                            </Typography>
                            {event.endDate && (
                                <Typography sx={{ color: "#4A5568", fontSize: "0.85rem" }}>
                                    até {formatDateTime(event.endDate)}
                                </Typography>
                            )}
                        </Box>
                    </Box>
                </Box>

                {/* Capacidade */}
                <Box sx={{
                    backgroundColor: "#F8F3E6", borderRadius: "14px",
                    p: 2.5, mb: 3,
                }}>
                    <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 1.5 }}>
                        <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                            <People sx={{ color: "#1B4332", fontSize: 20 }} />
                            <Typography sx={{ fontWeight: 600, color: "#1A1A1A" }}>
                                Vagas disponíveis
                            </Typography>
                        </Box>
                        <Typography sx={{ fontWeight: 700, color: "#1B4332", fontSize: "1.1rem" }}>
                            {event.capacity - registered} / {event.capacity}
                        </Typography>
                    </Box>
                    <LinearProgress
                        variant="determinate"
                        value={capacityPct}
                        sx={{
                            height: 8, borderRadius: 4,
                            backgroundColor: "#1B433220",
                            "& .MuiLinearProgress-bar": {
                                backgroundColor: capacityPct >= 90 ? "#E53E3E" : "#52B788",
                                borderRadius: 4,
                            },
                        }}
                    />
                    <Typography sx={{ fontSize: "0.8rem", color: "#4A5568", mt: 0.8 }}>
                        {capacityPct === 100 ? "Sem vagas disponíveis" : `${event.capacity - registered} lugares livres`}
                    </Typography>
                </Box>

                {/* Descrição */}
                {event.description && (
                    <Box sx={{ mb: 4 }}>
                        <Typography sx={{
                            fontFamily: "'Playfair Display', serif",
                            fontWeight: 600, fontSize: "1.1rem",
                            color: "#1A1A1A", mb: 1.5,
                        }}>
                            Sobre este evento
                        </Typography>
                        <Typography sx={{
                            color: "#4A5568", lineHeight: 1.8, fontSize: "1rem",
                            whiteSpace: "pre-line",
                        }}>
                            {event.description}
                        </Typography>
                    </Box>
                )}

                {subError && <Alert severity="error" sx={{ mb: 2, borderRadius: "10px" }}>{subError}</Alert>}

                {/* Botão subscrever */}
                {!token && canModifySubscription ? (
                    <Button
                        variant="contained" size="large" fullWidth
                        onClick={() => navigate("/login")}
                        sx={{
                            py: 1.6, fontSize: "1rem", fontWeight: 600,
                            backgroundColor: "#1B4332",
                            "&:hover": { backgroundColor: "#0A2318" },
                        }}
                    >
                        Inicia sessão para te inscreveres
                    </Button>
                ) : canModifySubscription ? (
                    <Button
                        variant={subscribed ? "outlined" : "contained"}
                        size="large" fullWidth
                        startIcon={subscribed ? <BookmarkAdded /> : <BookmarkAdd />}
                        onClick={handleSubscribe}
                        disabled={subLoading}
                        sx={{
                            py: 1.6, fontSize: "1rem", fontWeight: 600,
                            ...(subscribed ? {
                                color: "#1B4332", borderColor: "#1B4332",
                                "&:hover": { backgroundColor: "#1B433310" },
                            } : {
                                backgroundColor: "#1B4332",
                                "&:hover": { backgroundColor: "#0A2318" },
                            }),
                        }}
                    >
                        {subscribed ? "Inscrito ✓" : "Inscrever-me neste evento"}
                    </Button>
                ) : subscribed ? (
                    <Button
                        variant="outlined" size="large" fullWidth disabled
                        startIcon={<BookmarkAdded />}
                        sx={{ py: 1.6, fontSize: "1rem", fontWeight: 600, color: "#1B4332", borderColor: "#1B433240" }}
                    >
                        Inscrito ✓ — {unavailableReason}
                    </Button>
                ) : (
                    <Alert severity="info" sx={{ borderRadius: "10px" }}>{unavailableReason}</Alert>
                )}
            </Box>
        </Box>
    );
}