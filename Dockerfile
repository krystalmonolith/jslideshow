FROM eclipse-temurin:25-jre

# Download latest jslideshow JAR from GitHub releases
RUN apt-get update && apt-get install -y --no-install-recommends curl jq \
    && DOWNLOAD_URL=$(curl -fsSL https://api.github.com/repos/krystalmonolith/jslideshow/releases/latest \
       | jq -r '.assets[] | select(.name | endswith("-jar-with-dependencies.jar")) | .browser_download_url') \
    && curl -fsSL -o /opt/jslideshow.jar "$DOWNLOAD_URL" \
    && apt-get purge -y --auto-remove curl jq \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /data

ENTRYPOINT ["java", "-jar", "/opt/jslideshow.jar"]
