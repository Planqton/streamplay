# Build stage - cross-compile for ARM64 (runs natively on host)
FROM --platform=$BUILDPLATFORM rust:latest AS builder

WORKDIR /app

# Install ARM64 cross-compilation toolchain
RUN dpkg --add-architecture arm64 && \
    apt-get update && apt-get install -y \
    pkg-config \
    gcc-aarch64-linux-gnu \
    g++-aarch64-linux-gnu \
    libc6-dev-arm64-cross \
    && rm -rf /var/lib/apt/lists/*

RUN rustup target add aarch64-unknown-linux-gnu

# Configure cross-compilation for cc-rs and cargo
ENV CC_aarch64_unknown_linux_gnu=aarch64-linux-gnu-gcc
ENV CXX_aarch64_unknown_linux_gnu=aarch64-linux-gnu-g++
ENV CARGO_TARGET_AARCH64_UNKNOWN_LINUX_GNU_LINKER=aarch64-linux-gnu-gcc
ENV PKG_CONFIG_ALLOW_CROSS=1

# Copy manifests
COPY Cargo.toml Cargo.lock* ./

# Create dummy main.rs for dependency caching
RUN mkdir src && echo "fn main() {}" > src/main.rs

# Build dependencies
RUN cargo build --release --target aarch64-unknown-linux-gnu && rm -rf src

# Copy actual source code
COPY src ./src

# Build application
RUN touch src/main.rs && cargo build --release --target aarch64-unknown-linux-gnu

# Runtime stage
FROM debian:bookworm-slim

WORKDIR /app

# Install runtime dependencies
RUN apt-get update && apt-get install -y ca-certificates && rm -rf /var/lib/apt/lists/*

# Copy binary from builder (cross-compiled ARM64 binary)
COPY --from=builder /app/target/aarch64-unknown-linux-gnu/release/streamplay-api /app/streamplay-api

# Create data directory for SQLite
RUN mkdir -p /app/data

# Set environment variables
ENV DATABASE_URL=sqlite:/app/data/data.db?mode=rwc
ENV SERVER_PORT=3000

EXPOSE 3000

CMD ["/app/streamplay-api"]
