// main.jsx
import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import { ThemeProvider, CssBaseline, createTheme } from "@mui/material";
import App from "./App";
import Event from "./pages/Event";
import EventList from "./pages/EventList";
import CreateEvent from "./pages/CreateEvent";
import EventEdit from "./pages/EventEdit";

const theme = createTheme({
  palette: {
    mode: "light",
    primary: { main: "#08567e" },
    secondary: { main: "#ffffff" },
  },
  shape: { borderRadius: 12 },
});

ReactDOM.createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<App />}>
            <Route index element={<EventList />} />         {/* "/" */}
            <Route path="events/:id" element={<Event />} />  {/* "/events/:id" */}
            <Route path="events/:id/edit" element={<EventEdit />} />
            <Route path="new" element={<CreateEvent />} />   {/* "/new" */}
          </Route>
        </Routes>
      </BrowserRouter>
    </ThemeProvider>
  </React.StrictMode>
);
