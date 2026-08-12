import React from "react";

export default class ErrorBoundary extends React.Component {
    constructor(props) {
        super(props);
        this.state = { hasError: false };
    }

    static getDerivedStateFromError() {
        return { hasError: true };
    }

    componentDidCatch(error, info) {
        console.error("Erro não tratado na aplicação:", error, info);
    }

    render() {
        if (this.state.hasError) {
            return (
                <div style={{ textAlign: "center", padding: "4rem", fontFamily: "DM Sans" }}>
                    <h2>Ocorreu um erro inesperado.</h2>
                    <p>Tenta recarregar a página.</p>
                    <button onClick={() => window.location.reload()}>Recarregar</button>
                </div>
            );
        }

        return this.props.children;
    }
}
