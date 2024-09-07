# Base image with Java 11
FROM mcr.microsoft.com/vscode/devcontainers/java:11

# Install Android SDK dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    unzip \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# Set up Android SDK environment variables
ENV ANDROID_SDK_ROOT=/usr/local/android-sdk
ENV PATH=${ANDROID_SDK_ROOT}/cmdline-tools/tools/bin:${ANDROID_SDK_ROOT}/platform-tools:$PATH

# Download and install Android SDK command-line tools
RUN mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools \
    && curl -s https://dl.google.com/android/repository/commandlinetools-linux-8512546_latest.zip -o /tmp/tools.zip \
    && unzip /tmp/tools.zip -d ${ANDROID_SDK_ROOT}/cmdline-tools \
    && rm /tmp/tools.zip

# Accept Android SDK licenses
RUN yes | sdkmanager --licenses

# Install required Android SDK packages
RUN sdkmanager --install "platform-tools" "platforms;android-30" "build-tools;30.0.3" "system-images;android-30;google_apis;x86_64"
