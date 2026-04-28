import { useNavigate } from "react-router-dom";
import { Box, Button, Typography, Container, Grid } from "@mui/material";

const features = [
    { icon: "🌱", title: "Descobre causas", desc: "Encontra eventos de voluntariado perto de ti, filtrados pelo que te move." },
    { icon: "🤝", title: "Conecta pessoas", desc: "Promotores e voluntários numa só plataforma. Simples, direto, humano." },
    { icon: "📍", title: "Age localmente", desc: "Cada pequena ação conta. Começa na tua comunidade." },
];

export default function Landing() {
    const navigate = useNavigate();

    return (
        <Box sx={{ backgroundColor: "#F8F3E6", minHeight: "100vh", fontFamily: "'DM Sans', sans-serif" }}>

            {/* Header */}
            <Box component="header" sx={{
                display: "flex", justifyContent: "space-between", alignItems: "center",
                px: { xs: 3, md: 6 }, py: 3,
            }}>
                <Typography sx={{
                    fontFamily: "'Playfair Display', serif",
                    fontSize: "1.6rem", fontWeight: 700,
                    color: "#1B4332", letterSpacing: "-0.5px"
                }}>
                    V-Link
                </Typography>
                <Box sx={{ display: "flex", gap: 2 }}>
                    <Button variant="outlined" onClick={() => navigate("/login")} sx={{
                        color: "#1B4332", borderColor: "#1B4332",
                        "&:hover": { backgroundColor: "#1B4332", color: "#F8F3E6" }
                    }}>
                        Entrar
                    </Button>
                    <Button variant="contained" onClick={() => navigate("/register")} sx={{
                        backgroundColor: "#1B4332",
                        "&:hover": { backgroundColor: "#0A2318" }
                    }}>
                        Registar
                    </Button>
                </Box>
            </Box>

            {/* Hero */}
            <Box sx={{
                position: "relative", overflow: "hidden",
                px: { xs: 3, md: 10 }, pt: { xs: 6, md: 10 }, pb: { xs: 8, md: 14 },
                display: "flex", flexDirection: { xs: "column", md: "row" },
                alignItems: "center", gap: 6,
            }}>
                {/* Blob decorativo */}
                <Box sx={{
                    position: "absolute", top: -80, right: -80, width: 500, height: 500,
                    borderRadius: "50%", background: "radial-gradient(circle, #52B78840 0%, transparent 70%)",
                    pointerEvents: "none",
                }} />

                <Box sx={{ flex: 1, zIndex: 1 }}>
                    <Typography sx={{
                        fontFamily: "'Playfair Display', serif",
                        fontSize: { xs: "2.8rem", md: "4.2rem" },
                        fontWeight: 700, lineHeight: 1.1,
                        color: "#1A1A1A", mb: 3,
                    }}>
                        Voluntariado que<br />
                        <Box component="span" sx={{ color: "#1B4332", fontStyle: "italic" }}>
                            transforma.
                        </Box>
                    </Typography>
                    <Typography sx={{
                        fontSize: "1.15rem", color: "#4A5568",
                        maxWidth: 480, lineHeight: 1.7, mb: 5,
                    }}>
                        O V-Link liga voluntários a causas que importam.
                        Encontra eventos, envolve-te, faz a diferença — tudo num só lugar.
                    </Typography>
                    <Box sx={{ display: "flex", gap: 2, flexWrap: "wrap" }}>
                        <Button
                            variant="contained" size="large"
                            onClick={() => navigate("/register")}
                            sx={{
                                backgroundColor: "#1B4332", fontSize: "1.05rem",
                                px: 4, py: 1.5,
                                "&:hover": { backgroundColor: "#0A2318" },
                            }}
                        >
                            Começar agora
                        </Button>
                        <Button
                            variant="outlined" size="large"
                            onClick={() => navigate("/events")}
                            sx={{
                                color: "#1B4332", borderColor: "#1B4332",
                                fontSize: "1.05rem", px: 4, py: 1.5,
                                "&:hover": { backgroundColor: "#1B433210" },
                            }}
                        >
                            Ver eventos
                        </Button>
                    </Box>
                </Box>

                {/* Imagem hero */}
                <Box sx={{
                    flex: 1, zIndex: 1,
                    display: { xs: "none", md: "flex" },
                    justifyContent: "center",
                }}>
                    <Box sx={{
                        width: 460, height: 360, borderRadius: "24px",
                        overflow: "hidden", boxShadow: "0 24px 60px #1B433330",
                        position: "relative",
                    }}>
                        <img
                            src="https://images.unsplash.com/photo-1593113598332-cd288d649433?w=900&q=80"
                            alt="Voluntários a trabalhar juntos"
                            style={{ width: "100%", height: "100%", objectFit: "cover" }}
                        />
                        {/* Badge flutuante */}
                        <Box sx={{
                            position: "absolute", bottom: 20, left: 20,
                            backgroundColor: "rgba(255,255,255,0.95)",
                            borderRadius: "12px", px: 2.5, py: 1.5,
                            backdropFilter: "blur(8px)",
                            boxShadow: "0 4px 20px rgba(0,0,0,0.1)",
                        }}>
                            <Typography sx={{ fontSize: "0.75rem", color: "#4A5568", mb: 0.3 }}>
                                Eventos ativos
                            </Typography>
                            <Typography sx={{
                                fontSize: "1.4rem", fontWeight: 700,
                                color: "#1B4332", fontFamily: "'Playfair Display', serif"
                            }}>
                                248+
                            </Typography>
                        </Box>
                    </Box>
                </Box>
            </Box>

            {/* Divisor ondulado */}
            <Box sx={{ lineHeight: 0, mt: -2 }}>
                <svg viewBox="0 0 1440 60" xmlns="http://www.w3.org/2000/svg" preserveAspectRatio="none" style={{ width: "100%", height: 60 }}>
                    <path d="M0,30 C360,60 1080,0 1440,30 L1440,60 L0,60 Z" fill="#1B4332" />
                </svg>
            </Box>

            {/* Features */}
            <Box sx={{ backgroundColor: "#1B4332", py: { xs: 8, md: 10 }, px: { xs: 3, md: 10 } }}>
                <Typography sx={{
                    fontFamily: "'Playfair Display', serif",
                    fontSize: { xs: "2rem", md: "2.6rem" },
                    fontWeight: 700, color: "#F8F3E6",
                    textAlign: "center", mb: 8,
                }}>
                    Porquê o V-Link?
                </Typography>
                <Grid container spacing={4} justifyContent="center">
                    {features.map((f) => (
                        <Grid item xs={12} sm={4} key={f.title}>
                            <Box sx={{
                                backgroundColor: "rgba(255,255,255,0.07)",
                                borderRadius: "16px", p: 4,
                                border: "1px solid rgba(255,255,255,0.1)",
                                height: "100%", transition: "transform 0.2s",
                                "&:hover": { transform: "translateY(-4px)" },
                            }}>
                                <Typography sx={{ fontSize: "2.5rem", mb: 2 }}>{f.icon}</Typography>
                                <Typography sx={{
                                    fontFamily: "'Playfair Display', serif",
                                    fontSize: "1.3rem", fontWeight: 600,
                                    color: "#F8F3E6", mb: 1.5,
                                }}>
                                    {f.title}
                                </Typography>
                                <Typography sx={{ color: "#A8D5B5", lineHeight: 1.7 }}>
                                    {f.desc}
                                </Typography>
                            </Box>
                        </Grid>
                    ))}
                </Grid>
            </Box>

            {/* CTA final */}
            <Box sx={{
                backgroundColor: "#F8F3E6", py: { xs: 8, md: 12 },
                textAlign: "center", px: 3,
            }}>
                <Typography sx={{
                    fontFamily: "'Playfair Display', serif",
                    fontSize: { xs: "2rem", md: "2.8rem" },
                    fontWeight: 700, color: "#1A1A1A", mb: 2,
                }}>
                    Pronto para fazer a diferença?
                </Typography>
                <Typography sx={{ color: "#4A5568", mb: 5, fontSize: "1.1rem" }}>
                    Junta-te a centenas de voluntários e promotores em Portugal.
                </Typography>
                <Button
                    variant="contained" size="large"
                    onClick={() => navigate("/register")}
                    sx={{
                        backgroundColor: "#1B4332", fontSize: "1.1rem",
                        px: 5, py: 1.8,
                        "&:hover": { backgroundColor: "#0A2318" },
                    }}
                >
                    Criar conta gratuita
                </Button>
            </Box>

            {/* Footer */}
            <Box sx={{
                backgroundColor: "#0A2318", py: 3, textAlign: "center",
            }}>
                <Typography sx={{ color: "#52B788", fontSize: "0.85rem" }}>
                    © 2025 V-Link · Feito com 🌱 em Portugal
                </Typography>
            </Box>
        </Box>
    );
}