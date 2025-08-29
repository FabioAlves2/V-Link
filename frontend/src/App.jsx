import { Outlet, Link, useLocation } from "react-router-dom";
import {
  AppBar, Toolbar, Typography, Container, Button, Stack
} from "@mui/material";
import AddCircleOutlineIcon from "@mui/icons-material/AddCircleOutline";

export default function App() {
  const { pathname } = useLocation();

  return (
    <>
      <AppBar position="sticky" elevation={0}>
        <Toolbar>
          <Typography variant="h6" sx={{ flexGrow: 1 }}>
            V-LINK
          </Typography>

          <Stack direction="row" spacing={1}>
            <Button
              component={Link}
              to="/"
              color={pathname === "/" ? "inherit" : "secondary"}
              variant={pathname === "/" ? "outlined" : "text"}
            >
              List Events
            </Button>
            <Button
              component={Link}
              to="/new"
			        color={pathname === "/new" ? "inherit" : "secondary"}
              variant={pathname === "/new" ? "outlined" : "text"}
              startIcon={<AddCircleOutlineIcon />}
            >
              Create new Event
            </Button>
          </Stack>
        </Toolbar>
      </AppBar>

      <Container sx={{ py: 3 }}>
        <Outlet />
      </Container>
    </>
  );
}
