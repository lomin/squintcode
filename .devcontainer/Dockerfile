FROM --platform=linux/arm64 ubuntu:24.04

ARG DEBIAN_FRONTEND=noninteractive

# System packages
# - openjdk: Clojure/ClojureScript compilation and JVM tests
# - rlwrap: required by the `clj` launcher script
# - unzip: needed to install clj-kondo and the Dart SDK
# - sbcl, ecl: Common Lisp references for ucl's semantics
RUN apt-get update && apt-get install -y \
    ca-certificates \
    curl \
    git \
    openjdk-21-jdk-headless \
    ripgrep \
    rlwrap \
    sbcl \
    ecl \
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

# Dart SDK -- required: ClojureDart compiles to Dart, the ucl suite and the
# solutions run on it (`dart test`), and LeetCode takes the Dart submissions.
# Pinned by SHA-256 like Node; update version + checksum together (checksum:
# the .zip.sha256sum next to the zip in the dart-archive bucket).
ARG DART_VERSION=3.13.5
ARG DART_SHA256=19a731647c3ed55058ee46dde00330150e6a8729bb6121b4a31e86084c8a3e6d
RUN curl -fsSL -o /tmp/dart.zip \
    "https://storage.googleapis.com/dart-archive/channels/stable/release/${DART_VERSION}/sdk/dartsdk-linux-arm64-release.zip" \
    && echo "${DART_SHA256}  /tmp/dart.zip" | sha256sum -c - \
    && unzip -q /tmp/dart.zip -d /usr/local/lib \
    && rm /tmp/dart.zip \
    && /usr/local/lib/dart-sdk/bin/dart --version
ENV DART_SDK=/usr/local/lib/dart-sdk
ENV PATH="/usr/local/lib/dart-sdk/bin:${PATH}"

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

# node_modules is a named volume (see devcontainer.json), not part of the bind
# mount. Docker seeds an empty volume from the image's content at the mount
# point, ownership included, so creating the directory here is what lets npm
# write to it as vscode rather than hitting a root-owned mount.
RUN mkdir -p /workspaces/leetcode/node_modules \
    && chown -R vscode:vscode /workspaces

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
