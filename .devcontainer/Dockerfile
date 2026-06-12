FROM --platform=linux/arm64 ubuntu:24.04

ARG DEBIAN_FRONTEND=noninteractive

# System packages
# - openjdk: Clojure/ClojureScript compilation and JVM tests
# - rlwrap: required by the `clj` launcher script
# - unzip: needed to install clj-kondo
RUN apt-get update && apt-get install -y \
    ca-certificates \
    curl \
    git \
    openjdk-21-jdk-headless \
    ripgrep \
    rlwrap \
    sudo \
    unzip \
    xz-utils \
    && rm -rf /var/lib/apt/lists/*

# Node.js 24 LTS — required: squint and esbuild are npm packages, and
# Squint tests/builds run on Node (the LeetCode-identical JS runtime).
# Installed from the official dist tarball with a pinned SHA-256 instead of
# the NodeSource curl|bash script, so a compromised mirror or script cannot
# inject a tampered runtime. Update version + checksum together (checksums:
# https://nodejs.org/dist/v${NODE_VERSION}/SHASUMS256.txt).
ARG NODE_VERSION=24.16.0
ARG NODE_SHA256=524659219d6a207a7400f2bde15d19ba060ffbe0d32a8643319ad67e3bb64c78
RUN curl -fsSL -o /tmp/node.tar.xz \
    "https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-arm64.tar.xz" \
    && echo "${NODE_SHA256}  /tmp/node.tar.xz" | sha256sum -c - \
    && tar -xJf /tmp/node.tar.xz -C /usr/local --strip-components=1 --no-same-owner \
    && rm /tmp/node.tar.xz \
    && node --version

# Clojure CLI (latest stable)
RUN curl -fsSL https://download.clojure.org/install/linux-install.sh | bash -

# Babashka (primary build/test orchestrator, see bb.edn), pinned by SHA-256.
# Update version + checksum together (checksum: the .tar.gz.sha256 asset on
# the GitHub release).
ARG BB_VERSION=1.12.218
ARG BB_SHA256=e9e9190afb0dd33abbcd3aa6c1382184a88a5498800324719be3be6e1aa68302
RUN curl -fsSL -o /tmp/bb.tar.gz \
    "https://github.com/babashka/babashka/releases/download/v${BB_VERSION}/babashka-${BB_VERSION}-linux-aarch64-static.tar.gz" \
    && echo "${BB_SHA256}  /tmp/bb.tar.gz" | sha256sum -c - \
    && tar xzf /tmp/bb.tar.gz -C /usr/local/bin \
    && chmod +x /usr/local/bin/bb \
    && rm /tmp/bb.tar.gz

# clj-kondo 2026.01.19
RUN curl -fsSL -L -o /tmp/clj-kondo.zip \
    "https://github.com/clj-kondo/clj-kondo/releases/download/v2026.01.19/clj-kondo-2026.01.19-linux-aarch64.zip" \
    && unzip -o /tmp/clj-kondo.zip -d /usr/local/bin \
    && chmod +x /usr/local/bin/clj-kondo \
    && rm /tmp/clj-kondo.zip

ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64

# Create non-root user
RUN useradd -m -s /bin/bash vscode \
    && echo "vscode ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers.d/vscode

USER vscode
ENV PATH="/home/vscode/.local/bin:${PATH}"
WORKDIR /home/vscode

# bbin (babashka package manager)
RUN mkdir -p /home/vscode/.local/bin \
    && curl -o- -L https://raw.githubusercontent.com/babashka/bbin/v0.2.5/bbin > /home/vscode/.local/bin/bbin \
    && chmod +x /home/vscode/.local/bin/bbin

# clj-nrepl-eval (used by Claude Code for REPL evaluation, see CLAUDE.md)
RUN bbin install https://github.com/bhauman/clojure-mcp-light.git \
    --tag v0.2.2 \
    --as clj-nrepl-eval \
    --main-opts '["-m" "clojure-mcp-light.nrepl-eval"]'
