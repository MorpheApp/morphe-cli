# 🔨️ Building

Build Morphe CLI from source.

## 📝 Requirements

- Java Development Kit 11 (Azul Zulu JRE or OpenJDK)

## 🏗️ Building

To build Morphe CLI, follow these steps:

1. Clone the repository:

   ```bash
   git clone git@github.com:MorpheApp/morphe-cli.git
   cd morphe-cli
   ```

2. Build the project:

   ```bash
    ./gradlew build
   ```

> [!NOTE]
> If the build fails due to authentication, you may need to authenticate to GitHub Packages.
> Create a PAT with the scope `read:packages` [here](https://github.com/settings/tokens/new?scopes=read:packages&description=Morphe) and add your token to ~/.gradle/gradle.properties.
>
> Example `gradle.properties` file:
>
> ```properties
> gpr.user = user
> gpr.key = key
> ```

After the build succeeds, the built JAR file will be located at `build/libs/morphe-cli-<version>-all.jar`.
