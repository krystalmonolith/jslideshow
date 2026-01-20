# Maven Site Plugin Configuration Guide

This guide explains the Maven Site configuration added to your pom.xml and how to use it to generate comprehensive project documentation.

## What's Been Added

### 1. Build Plugins

**maven-site-plugin** (version 3.12.1)
- Generates the project website/documentation
- Configured for English locale

**maven-javadoc-plugin** (version 3.6.3)
- Generates JavaDoc from your source code comments
- Shows private members for complete documentation
- Creates a JAR of JavaDocs during packaging

### 2. Reporting Plugins

The `<reporting>` section configures what information appears on your generated site:

#### maven-project-info-reports-plugin
Generates general project information:
- **Index** - Project overview/home page
- **Summary** - Project summary
- **Dependencies** - List of project dependencies
- **Dependency Info** - How to use your project as a dependency
- **Team** - Developer information
- **Licenses** - License information
- **Plugins** - Maven plugins used

#### maven-javadoc-plugin
- Generates browsable API documentation from JavaDoc comments in source code
- Links to source cross-references

#### maven-jxr-plugin
- Creates cross-referenced HTML version of source code
- Makes source code browsable with syntax highlighting
- Links between JavaDoc and source code

#### maven-surefire-report-plugin
- Generates test execution reports
- Shows passed/failed tests (when you add tests)

#### maven-pmd-plugin
- Static code analysis
- Finds potential bugs and code smells
- Checks for unused variables, empty catch blocks, etc.

#### spotbugs-maven-plugin
- Advanced static analysis
- Finds potential bugs using patterns
- Successor to FindBugs

#### maven-checkstyle-plugin
- Code style checking
- Uses Google Java Style Guide by default
- Ensures consistent code formatting

#### jdepend-maven-plugin
- Package dependency analysis
- Shows package coupling and cycles

## How to Generate the Site

### Generate All Reports
```bash
mvn site
```

The site will be generated in: `target/site/index.html`

### Generate and View in Browser
```bash
# Generate site
mvn site

# View in browser (Linux)
xdg-open target/site/index.html

# View in browser (macOS)
open target/site/index.html

# View in browser (Windows)
start target/site/index.html
```

### Generate Individual Reports

**Just JavaDoc:**
```bash
mvn javadoc:javadoc
# Output: target/site/apidocs/index.html
```

**Just Source Cross-Reference:**
```bash
mvn jxr:jxr
# Output: target/site/xref/index.html
```

**Just PMD Analysis:**
```bash
mvn pmd:pmd
# Output: target/site/pmd.html
```

**Just Checkstyle:**
```bash
mvn checkstyle:checkstyle
# Output: target/site/checkstyle.html
```

## Site Structure

After running `mvn site`, you'll get:

```
target/site/
├── index.html              # Main page
├── project-info.html       # Project information
├── dependencies.html       # Dependency report
├── team.html              # Team/developers
├── licenses.html          # License information
├── plugins.html           # Maven plugins
├── apidocs/               # JavaDoc
│   └── index.html
├── xref/                  # Source cross-reference
│   └── index.html
├── pmd.html               # PMD report
├── spotbugs.html          # SpotBugs report
├── checkstyle.html        # Checkstyle report
├── jdepend-report.html    # JDepend report
└── css/                   # Styling
```

## Customizing the Site

### Add a Custom Site Descriptor

Create `src/site/site.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project name="JSlideshow">
    <bannerLeft>
        <n>JSlideshow</n>
        <src>images/logo.png</src>
        <href>index.html</href>
    </bannerLeft>
    
    <body>
        <links>
            <item name="GitHub" href="https://github.com/krystalmonolith/jslideshow"/>
        </links>
        
        <menu name="Overview">
            <item name="About" href="index.html"/>
            <item name="Usage" href="usage.html"/>
        </menu>
        
        <menu ref="reports"/>
        
        <menu name="Development">
            <item name="Source Code" href="xref/index.html"/>
            <item name="JavaDoc" href="apidocs/index.html"/>
        </menu>
    </body>
</project>
```

### Add Custom Pages

Create markdown or APT files in `src/site/markdown/` or `src/site/apt/`:

**src/site/markdown/usage.md:**
```markdown
# Usage Guide

## Quick Start

Download the JAR and run:

\`\`\`bash
java -jar jslideshow-1.1.0-jar-with-dependencies.jar /path/to/images
\`\`\`

## Examples

...
```

## Best Practices

### 1. Write JavaDoc Comments

Add JavaDoc to your classes and methods:

```java
/**
 * Creates a video slideshow from JPG images with dissolve transitions.
 * This class processes all JPG files in a directory and generates
 * an MP4 video with smooth transitions between images.
 * 
 * @author krystalmonolith
 * @version 1.1.0
 * @since 1.0.0
 */
public class SlideshowCreator {
    
    /**
     * Generate output filename with timestamp.
     * 
     * @return Filename in format: YYYYMMDD'T'hhmmss-output.mp4
     */
    private static String generateOutputFilename() {
        // ...
    }
    
    /**
     * Blend two images together with specified alpha.
     * 
     * @param img1 First image (from)
     * @param img2 Second image (to)
     * @param alpha Blend factor (0.0 = all img1, 1.0 = all img2)
     * @return Blended image
     */
    private BufferedImage blendImages(BufferedImage img1, BufferedImage img2, float alpha) {
        // ...
    }
}
```

### 2. Update Project Information

In pom.xml, add:
- `<url>` - Project website
- `<developers>` - Team information
- `<licenses>` - License details
- `<scm>` - Source control info

### 3. Regular Site Generation

Generate the site regularly during development:
```bash
# After significant changes
mvn clean install site

# Check for code quality issues
mvn pmd:check checkstyle:check spotbugs:check
```

## Integrating with CI/CD

### GitHub Actions

Add to `.github/workflows/build.yml`:

```yaml
name: Build and Site

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 24
      uses: actions/setup-java@v3
      with:
        java-version: '24'
        distribution: 'temurin'
    
    - name: Build with Maven
      run: mvn clean install
    
    - name: Generate Site
      run: mvn site
    
    - name: Deploy Site to GitHub Pages
      uses: peaceiris/actions-gh-pages@v3
      with:
        github_token: ${{ secrets.GITHUB_TOKEN }}
        publish_dir: ./target/site
```

## Publishing the Site

### To GitHub Pages

```bash
# Generate site
mvn site

# Deploy to gh-pages branch
mvn scm-publish:publish-scm
```

### To Web Server

```bash
# Generate site
mvn site

# Copy to web server
scp -r target/site/* user@server:/var/www/html/jslideshow/
```

## Troubleshooting

### "Source option 24 is not supported"

Some plugins may not support Java 24 yet. You can:
1. Disable that specific plugin
2. Wait for plugin updates
3. Use Java 21 for site generation only

### Memory Issues During Site Generation

Increase Maven memory:
```bash
export MAVEN_OPTS="-Xmx2g"
mvn site
```

### Slow Site Generation

Disable expensive reports during development:
```bash
# Skip PMD, SpotBugs, and Checkstyle
mvn site -Dpmd.skip=true -Dspotbugs.skip=true -Dcheckstyle.skip=true
```

## Minimal Site Configuration

If you want just JavaDoc and basic info, use this simpler `<reporting>`:

```xml
<reporting>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-project-info-reports-plugin</artifactId>
            <version>3.5.0</version>
        </plugin>
        
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-javadoc-plugin</artifactId>
            <version>3.6.3</version>
        </plugin>
        
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-jxr-plugin</artifactId>
            <version>3.3.2</version>
        </plugin>
    </plugins>
</reporting>
```

## Summary

**To generate comprehensive documentation:**
```bash
mvn clean install site
```

**To view:**
```bash
open target/site/index.html
```

The site will include:
- Project information
- JavaDoc API documentation
- Cross-referenced source code
- Code quality reports
- Dependency analysis

This provides professional documentation for your project!
