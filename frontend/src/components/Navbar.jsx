import { useState } from "react";
import { Link, useNavigate, useLocation } from "react-router-dom";
import {
    AppBar, Toolbar, Typography, Button, Box,
    IconButton, Menu, MenuItem, Avatar, Divider, Chip
} from "@mui/material";
import { KeyboardArrowDown, Logout, AddCircleOutline } from "@mui/icons-material";
import { useAuth } from "../context/authContext";

const roleLabel = { VOLUNTEER: "Voluntário", PROMOTER: "Promotor" };
const roleColor = { VOLUNTEER: "#52B788", PROMOTER: "#D4A853" };

export default function Navbar() {
    const { role, logout, token } = useAuth();
    const navigate = useNavigate();
    const location = useLocation();
    const [anchorEl, setAnchorEl] = useState(null);

    if (!token) return null;

    const isActive = (path) => location.pathname.startsWith(path);

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

                    {/* Só visível para PROMOTER */}
                    {role === "PROMOTER" && (
                        <Button
                            component={Link} to="/new"
                            startIcon={<AddCircleOutline sx={{ fontSize: "1rem" }} />}
                            sx={{
                                color: isActive("/new") ? "#D4A853" : "rgba(255,255,255,0.75)",
                                fontWeight: isActive("/new") ? 600 : 400,
                                fontSize: "0.95rem",
                                "&:hover": { color: "#F8F3E6", backgroundColor: "rgba(255,255,255,0.08)" },
                            }}
                        >
                            Criar evento
                        </Button>
                    )}
                </Box>

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