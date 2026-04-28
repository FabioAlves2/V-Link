import { Outlet } from "react-router-dom";
import Navbar from "./components/Navbar";
import { Box } from "@mui/material";

export default function App() {
  return (
    <Box sx={{ minHeight: "100vh", backgroundColor: "#F8F3E6" }}>
      <Navbar />
      <Box component="main" sx={{ pt: 2, px: { xs: 2, md: 4 } }}>
        <Outlet />
      </Box>
    </Box>
  );
}