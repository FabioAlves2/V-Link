import { useEffect, useState } from "react";
import { Link, useNavigate, useLocation } from "react-router-dom";
import {
    AppBar, Toolbar, Typography, Button, Box,
    IconButton, Menu, MenuItem, Avatar, Divider, Chip, Badge
} from "@mui/material";
import { KeyboardArrowDown, Logout, Person, NotificationsNone } from "@mui/icons-material";
import { useAuth } from "../context/authContext";
import { getNotifications, getUnreadCount, markNotificationRead } from "../api/notification";

const roleLabel = { VOLUNTEER: "Voluntário", PROMOTER: "Promotor" };
const roleColor = { VOLUNTEER: "#52B788", PROMOTER: "#D4A853" };
const NOTIFICATIONS_POLL_MS = 30000;

export default function Navbar() {
    const { role, logout, token } = useAuth();
    const navigate = useNavigate();
    const location = useLocation();
    const [anchorEl, setAnchorEl] = useState(null);
    const [notifAnchorEl, setNotifAnchorEl] = useState(null);
    const [notifications, setNotifications] = useState([]);
    const [unreadCount, setUnreadCount] = useState(0);

    useEffect(() => {
        if (!token) return;
        const poll = () => getUnreadCount().then(({ data }) => setUnreadCount(data.count)).catch(() => {});
        poll();
        const interval = setInterval(poll, NOTIFICATIONS_POLL_MS);
        return () => clearInterval(interval);
    }, [token]);

    const isActive = (path) => location.pathname.startsWith(path);

    // Visitante anónimo (ex.: link de evento partilhado) — cabeçalho mínimo em vez de nada,
    // para dar contexto de marca e um caminho óbvio para entrar; sem os links/menus que
    // dependem de sessão iniciada.
    if (!token) {
        return (
            <AppBar position="sticky" elevation={0} sx={{
                backgroundColor: "#1B4332",
                borderBottom: "1px solid rgba(255,255,255,0.08)",
            }}>
                <Toolbar sx={{ px: { xs: 2, md: 4 }, minHeight: "64px !important" }}>
                    <Typography
                        component={Link} to="/"
                        sx={{
                            fontFamily: "'Playfair Display', serif",
                            fontSize: "1.5rem", fontWeight: 700,
                            color: "#F8F3E6", textDecoration: "none",
                            letterSpacing: "-0.3px", flexGrow: 1,
                        }}
                    >
                        V-Link
                    </Typography>
                    <Button
                        component={Link} to="/login"
                        variant="outlined"
                        sx={{
                            color: "#F8F3E6", borderColor: "rgba(255,255,255,0.4)",
                            "&:hover": { borderColor: "#F8F3E6", backgroundColor: "rgba(255,255,255,0.08)" },
                        }}
                    >
                        Entrar
                    </Button>
                </Toolbar>
            </AppBar>
        );
    }

    const openNotifications = (e) => {
        setNotifAnchorEl(e.currentTarget);
        getNotifications().then(({ data }) => setNotifications(data)).catch(() => {});
    };

    const handleNotificationClick = async (notification) => {
        setNotifAnchorEl(null);
        if (!notification.read) {
            setUnreadCount((c) => Math.max(0, c - 1));
            markNotificationRead(notification.id).catch(() => {});
        }
        if (notification.eventId) navigate(`/events/${notification.eventId}`);
    };

    return (
        <AppBar position="sticky" elevation={0} sx={{
            backgroundColor: "#1B4332",
            borderBottom: "1px solid rgba(255,255,255,0.08)",
        }}>
            <Toolbar sx={{ px: { xs: 2, md: 4 }, minHeight: "64px !important" }}>

                {/* Logo */}
                <Typography
                    component={Link} to="/events"
                    sx={{
                        fontFamily: "'Playfair Display', serif",
                        fontSize: "1.5rem", fontWeight: 700,
                        color: "#F8F3E6", textDecoration: "none",
                        mr: 5, letterSpacing: "-0.3px",
                        flexShrink: 0,
                    }}
                >
                    V-Link
                </Typography>

                {/* Links de navegação */}
                <Box sx={{ display: "flex", gap: 1, flexGrow: 1 }}>
                    <Button
                        component={Link} to="/events"
                        sx={{
                            color: isActive("/events") ? "#52B788" : "rgba(255,255,255,0.75)",
                            fontWeight: isActive("/events") ? 600 : 400,
                            fontSize: "0.95rem",
                            "&:hover": { color: "#F8F3E6", backgroundColor: "rgba(255,255,255,0.08)" },
                        }}
                    >
                        Eventos
                    </Button>

                    <Button
                        component={Link} to="/subscriptions"
                        sx={{
                            color: isActive("/subscriptions") ? "#52B788" : "rgba(255,255,255,0.75)",
                            fontWeight: isActive("/subscriptions") ? 600 : 400,
                            fontSize: "0.95rem",
                            "&:hover": { color: "#F8F3E6", backgroundColor: "rgba(255,255,255,0.08)" },
                        }}
                    >
                        As minhas inscrições
                    </Button>

                    <Button
                        component={Link} to="/favorites"
                        sx={{
                            color: isActive("/favorites") ? "#52B788" : "rgba(255,255,255,0.75)",
                            fontWeight: isActive("/favorites") ? 600 : 400,
                            fontSize: "0.95rem",
                            "&:hover": { color: "#F8F3E6", backgroundColor: "rgba(255,255,255,0.08)" },
                        }}
                    >
                        Favoritos
                    </Button>

                    {/* Só visível para VOLUNTEER */}
                    {role === "VOLUNTEER" && (
                        <Button
                            component={Link} to="/my-dashboard"
                            sx={{
                                color: isActive("/my-dashboard") ? "#52B788" : "rgba(255,255,255,0.75)",
                                fontWeight: isActive("/my-dashboard") ? 600 : 400,
                                fontSize: "0.95rem",
                                "&:hover": { color: "#F8F3E6", backgroundColor: "rgba(255,255,255,0.08)" },
                            }}
                        >
                            O meu painel
                        </Button>
                    )}

                    {/* Só visível para PROMOTER — "Criar evento" já está no Painel do organizador */}
                    {role === "PROMOTER" && (
                        <Button
                            component={Link} to="/dashboard"
                            sx={{
                                color: isActive("/dashboard") ? "#D4A853" : "rgba(255,255,255,0.75)",
                                fontWeight: isActive("/dashboard") ? 600 : 400,
                                fontSize: "0.95rem",
                                "&:hover": { color: "#F8F3E6", backgroundColor: "rgba(255,255,255,0.08)" },
                            }}
                        >
                            Painel do organizador
                        </Button>
                    )}
                </Box>

                {/* Notificações */}
                <IconButton onClick={openNotifications} sx={{ color: "rgba(255,255,255,0.85)", mr: 1 }}>
                    <Badge badgeContent={unreadCount} color="error" max={9}>
                        <NotificationsNone />
                    </Badge>
                </IconButton>

                <Menu
                    anchorEl={notifAnchorEl} open={Boolean(notifAnchorEl)}
                    onClose={() => setNotifAnchorEl(null)}
                    PaperProps={{
                        sx: {
                            borderRadius: "12px", mt: 1,
                            boxShadow: "0 8px 32px rgba(0,0,0,0.12)",
                            minWidth: 300, maxWidth: 360, maxHeight: 400,
                        }
                    }}
                    transformOrigin={{ horizontal: "right", vertical: "top" }}
                    anchorOrigin={{ horizontal: "right", vertical: "bottom" }}
                >
                    <Box sx={{ px: 2, py: 1.5 }}>
                        <Typography sx={{ fontWeight: 600, fontSize: "0.9rem", color: "#1B4332" }}>
                            Notificações
                        </Typography>
                    </Box>
                    <Divider />
                    {notifications.length === 0 ? (
                        <Box sx={{ px: 2, py: 3, textAlign: "center" }}>
                            <Typography sx={{ fontSize: "0.85rem", color: "#4A5568" }}>
                                Sem notificações.
                            </Typography>
                        </Box>
                    ) : (
                        notifications.map((n) => (
                            <MenuItem
                                key={n.id}
                                onClick={() => handleNotificationClick(n)}
                                sx={{
                                    py: 1.2, whiteSpace: "normal",
                                    backgroundColor: n.read ? "transparent" : "#1B433208",
                                }}
                            >
                                <Typography sx={{ fontSize: "0.85rem", color: "#1A1A1A" }}>
                                    {n.message}
                                </Typography>
                            </MenuItem>
                        ))
                    )}
                </Menu>

                {/* Avatar + menu */}
                <Box
                    onClick={(e) => setAnchorEl(e.currentTarget)}
                    sx={{
                        display: "flex", alignItems: "center", gap: 1,
                        cursor: "pointer", px: 1.5, py: 0.8,
                        borderRadius: "10px",
                        "&:hover": { backgroundColor: "rgba(255,255,255,0.08)" },
                        transition: "background 0.15s",
                    }}
                >
                    <Avatar sx={{
                        width: 32, height: 32,
                        backgroundColor: "#52B788",
                        fontSize: "0.85rem", fontWeight: 700,
                        color: "#1B4332",
                    }}>
                        {role?.[0]}
                    </Avatar>
                    <Chip
                        label={roleLabel[role] || role}
                        size="small"
                        sx={{
                            backgroundColor: `${roleColor[role]}22`,
                            color: roleColor[role] || "#F8F3E6",
                            fontWeight: 600, fontSize: "0.75rem",
                            height: 22, border: `1px solid ${roleColor[role]}44`,
                        }}
                    />
                    <KeyboardArrowDown sx={{
                        color: "rgba(255,255,255,0.6)", fontSize: "1.1rem",
                        transform: anchorEl ? "rotate(180deg)" : "rotate(0deg)",
                        transition: "transform 0.2s",
                    }} />
                </Box>

                <Menu
                    anchorEl={anchorEl} open={Boolean(anchorEl)}
                    onClose={() => setAnchorEl(null)}
                    PaperProps={{
                        sx: {
                            borderRadius: "12px", mt: 1,
                            boxShadow: "0 8px 32px rgba(0,0,0,0.12)",
                            minWidth: 180,
                        }
                    }}
                    transformOrigin={{ horizontal: "right", vertical: "top" }}
                    anchorOrigin={{ horizontal: "right", vertical: "bottom" }}
                >
                    <Box sx={{ px: 2, py: 1.5 }}>
                        <Typography sx={{ fontSize: "0.75rem", color: "#4A5568" }}>
                            Sessão iniciada como
                        </Typography>
                        <Typography sx={{ fontWeight: 600, fontSize: "0.9rem", color: "#1B4332" }}>
                            {roleLabel[role] || role}
                        </Typography>
                    </Box>
                    <Divider />
                    <MenuItem
                        component={Link} to="/profile"
                        onClick={() => setAnchorEl(null)}
                        sx={{ gap: 1.5, py: 1.5 }}
                    >
                        <Person fontSize="small" />
                        O meu perfil
                    </MenuItem>
                    <Divider />
                    <MenuItem
                        onClick={() => { logout(); navigate("/"); setAnchorEl(null); }}
                        sx={{
                            color: "#E53E3E", gap: 1.5, py: 1.5,
                            "&:hover": { backgroundColor: "#FFF5F5" },
                        }}
                    >
                        <Logout fontSize="small" />
                        Terminar sessão
                    </MenuItem>
                </Menu>
            </Toolbar>
        </AppBar>
    );
}