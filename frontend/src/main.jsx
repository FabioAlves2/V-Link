import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { ThemeProvider, CssBaseline, createTheme } from "@mui/material";

import App from "./App";
import Landing from "./pages/Landing";
import Login from "./pages/Login";
import Register from "./pages/Register";
import Event from "./pages/Event";
import EventList from "./pages/EventList";
import CreateEvent from "./pages/CreateEvent";
import EventEdit from "./pages/EventEdit";
import MySubscriptions from "./pages/MySubscriptions";
import MyFavorites from "./pages/MyFavorites";
import VolunteerDashboard from "./pages/VolunteerDashboard";
import Profile from "./pages/Profile";
import OrganizerDashboard from "./pages/OrganizerDashboard";
import EventSubscribers from "./pages/EventSubscribers";

import { AuthProvider, useAuth } from "./context/authContext";
import ErrorBoundary from "./components/ErrorBoundary";

// eslint-disable-next-line react-refresh/only-export-components -- route guards live alongside the router that uses them
function ProtectedRoute({ children, requiredRole }) {
  const { token, role } = useAuth();
  if (!token) return <Navigate to="/login" />;
  if (requiredRole && role !== requiredRole) return <Navigate to="/unauthorized" />;
  return children;
}

// eslint-disable-next-line react-refresh/only-export-components -- route guards live alongside the router that uses them
function AuthRoute({ children }) {
  const { token } = useAuth();
  if (token) return <Navigate to="/events" />;
  return children;
}

const theme = createTheme({
  palette: {
    mode: "light",
    primary: { main: "#1B4332", light: "#52B788", dark: "#0A2318" },
    secondary: { main: "#D4A853" },
    background: { default: "#F8F3E6", paper: "#FFFFFF" },
    text: { primary: "#1A1A1A", secondary: "#4A5568" },
  },
  typography: {
    fontFamily: "'DM Sans', sans-serif",
    h1: { fontFamily: "'Playfair Display', serif", fontWeight: 700 },
    h2: { fontFamily: "'Playfair Display', serif", fontWeight: 700 },
    h3: { fontFamily: "'Playfair Display', serif", fontWeight: 600 },
    h4: { fontFamily: "'Playfair Display', serif", fontWeight: 600 },
  },
  shape: { borderRadius: 10 },
  components: {
    MuiButton: {
      styleOverrides: {
        root: {
          textTransform: "none",
          fontWeight: 600,
          fontSize: "1rem",
          padding: "10px 28px",
          borderRadius: "8px",
        },
      },
    },
  },
});

ReactDOM.createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <ErrorBoundary>
    <AuthProvider>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        {/* Google Fonts */}
        <style>{`
                    @import url('https://fonts.googleapis.com/css2?family=Playfair+Display:ital,wght@0,400;0,600;0,700;1,400&family=DM+Sans:wght@300;400;500;600&display=swap');
                    * { box-sizing: border-box; }
                    body { background-color: #F8F3E6; }
                `}</style>
        <BrowserRouter>
          <Routes>
            {/* Públicas */}
            <Route path="/" element={<Landing />} />
            <Route path="/login" element={<AuthRoute><Login /></AuthRoute>} />
            <Route path="/register" element={<AuthRoute><Register /></AuthRoute>} />

            {/* App com Navbar */}
            <Route path="/" element={<App />}>
              {/* Voluntário + Promotor */}
              <Route path="events" element={<ProtectedRoute><EventList /></ProtectedRoute>} />
              {/* Pública: página de detalhe pode ser vista por um visitante anónimo (link partilhado);
                  Event.jsx trata internamente o que mostrar sem sessão iniciada. */}
              <Route path="events/:id" element={<Event />} />
              <Route path="profile" element={<ProtectedRoute><Profile /></ProtectedRoute>} />
              <Route path="subscriptions" element={<ProtectedRoute><MySubscriptions /></ProtectedRoute>} />
              <Route path="favorites" element={<ProtectedRoute><MyFavorites /></ProtectedRoute>} />
              <Route path="my-dashboard" element={<ProtectedRoute><VolunteerDashboard /></ProtectedRoute>} />

              {/* Só Promotor */}
              <Route path="new" element={
                <ProtectedRoute requiredRole="PROMOTER">
                  <CreateEvent />
                </ProtectedRoute>
              } />
              <Route path="events/:id/edit" element={
                <ProtectedRoute requiredRole="PROMOTER">
                  <EventEdit />
                </ProtectedRoute>
              } />
              <Route path="dashboard" element={
                <ProtectedRoute requiredRole="PROMOTER">
                  <OrganizerDashboard />
                </ProtectedRoute>
              } />
              <Route path="events/:id/subscribers" element={
                <ProtectedRoute requiredRole="PROMOTER">
                  <EventSubscribers />
                </ProtectedRoute>
              } />
            </Route>

            <Route path="/unauthorized" element={
              <div style={{ textAlign: "center", padding: "4rem", fontFamily: "DM Sans" }}>
                <h2>Sem permissão para aceder a esta página.</h2>
              </div>
            } />
            <Route path="*" element={<Navigate to="/" />} />
          </Routes>
        </BrowserRouter>
      </ThemeProvider>
    </AuthProvider>
    </ErrorBoundary>
  </React.StrictMode>
);