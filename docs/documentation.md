<h1 align="center">Morphe Desktop Documentation</h1>

This is the complete documentation for Morphe Desktop. It covers all the CLI sub-commands, flags, GUI usage
and common workflows. If you're brand new, it is recommended to start with the [first-run](../README.md#first-run) section
first, then come back here.

Now that you have gone through with your first run, lets dig deeper to understand how the magic happens and how you can make it even better!

<details open>
<summary><h2 id="table-of-contents">Table of contents</h2></summary>

- [Prerequisites](#prerequisites)
- [CLI](#cli)
  - [General flags](#general-flags)
  - [patch](#subcommand-1-patch)
  - [list-patches](#subcommand-2-list-patches)
  - [list-versions](#subcommand-3-list-versions)
  - [options-create](#subcommand-4-options-create)
  - [utility](#subcommand-5-utility)
    - [install](#utility-install)
    - [uninstall](#utility-uninstall)
  - [Value Types Reference](#value-types-reference)
- [GUI](#gui)

</details>


<details open>
<summary><h2 id="prerequisites">Prerequisites</h2></summary>

1. [Required] Java Runtime Environment 11 or above ([Azul Zulu JRE](https://www.azul.com/downloads/?version=java-11-lts&package=jre#zulu) or [OpenJDK](https://jdk.java.net/archive/)).
2. [Required] Morphe Desktop jar file (morphe-desktop-*-all.jar). You can download the most recent stable version of Morphe Desktop from [here](https://github.com/MorpheApp/morphe-cli/releases/latest).
3. [Required] Patches mpp file (patches-*.mpp). You can download the latest stable patch file from [here](https://github.com/MorpheApp/morphe-patches/releases/latest).
4. [Required] Desired app file (app.apk). You can download your apk from [APK Mirror](https://www.apkmirror.com/).
5. [Optional] [Android Debug Bridge (ADB)](https://developer.android.com/studio/command-line/adb) Only if you want to install the patched APK file on your device

</details>


<details open>
<summary><h2 id="cli">CLI</h2></summary>

The CLI is small yet extremely powerful. Often, new features will first appear in the CLI and then will be slowly implemented onto the GUI. Hence, getting a hang of the CLI is very advantageous.

The CLI has some general flags but is mainly divided into 5 main sub-commands (and they all lived in harmony, until the fire nation attacked. Caught that reference?):

![img.png](images/documentation/cli_overview.png)

### General flags:

#### 1. `-h`, `--help`:

Shows all the general flags and sub commands available.
```
java -jar morphe-desktop-*-all.jar --help
```

#### 2. `-V`, `--version`:

Shows the current version of the morphe-desktop.jar
```
java -jar morphe-desktop-*-all.jar --version
```

<details open>
<summary><h3 id="subcommand-1-patch">Subcommand 1: `patch`</h3></summary>

This is the most fundamental sub-command. Add the `patch` keyword to run this sub-command.
```
java -jar morphe-desktop-*-all.jar patch [flag/s]
```

Here is a quick lookup for all the flags under this subcommand:

| Flag                           | Description                                                   |
|--------------------------------|---------------------------------------------------------------|
| `-p`, `--patches`              | Paths to .mpp file                                            |
| *(positional arg)*             | APK file to patch                                             |
| `-o`, `--out`                  | Path to save the patched APK to                               |
| `-e`, `--enable`               | Enable a patch by name                                        |
| `--ei`                         | Enable a patch by index                                       |
| `-d`, `--disable`              | Disable a patch by name                                       |
| `--di`                         | Disable a patch by index                                      |
| `-O`, `--options`              | Set patch option values (e.g. `-Okey=value`)                  |
| `--exclusive`                  | Disable all patches except explicitly enabled ones            |
| `-f`, `--force`                | Skip APK version compatibility check                          |
| `-i`, `--install`              | Install to ADB device (optional serial)                       |
| `--mount`                      | Install by mounting over existing app (requires root)         |
| `--keystore`                   | Path to keystore file for signing                             |
| `--keystore-password`          | Keystore password                                             |
| `--keystore-entry-alias`       | Alias of the key pair in the keystore                         |
| `--keystore-entry-password`    | Password for the keystore entry                               |
| `--signer`                     | Signer name in the APK signature                              |
| `--unsigned`                   | Skip signing the final APK                                    |
| `-t`, `--temporary-files-path` | Path to store temp files                                      |
| `--purge`                      | Delete temp files after patching                              |
| `--custom-aapt2-binary`        | Path to custom AAPT2 binary for non-x86 architectures         |
| `--force-apktool`              | Use apktool instead of arsclib for resource compilation       |
| `--striplibs`                  | Architectures to keep, comma-separated (e.g. `arm64-v8a,x86`) |
| `--continue-on-error`          | Continue patching if a patch fails                            |
| `--options-file`               | Path to options JSON file                                     |
| `--options-update`             | Auto-update options JSON file after patching                  |
| `-r`, `--result-file`          | Path to save patching result JSON                             |

> [!NOTE]
> The examples used for each flag below only show the usage of that specific flag, but in practice, you'll almost always combine multiple flags together to customize your patching. Here's an example of a more complete command:
> ```
> java -jar morphe-desktop-*-all.jar patch -p patches.mpp -o your_app_patched.apk --striplibs arm64-v8a --force --continue-on-error -d "change package name" -d "spoof signature" "your_app.apk"
> ```


#### 1. `-p`, `--patches`:
Required: Yes

Default: —

This flag is used to specify the patch file to patch your apk. You can pass multiple .mpp files if needed. Currently, we only support 1 patch file, but we plan to expand its capabilities 
```
java -jar morphe-desktop-*-all.jar patch --patches patches-*.mpp your_app.apk
```


#### 2. Positional argument (APK file):
Required: Yes

Default: —

The APK file you want to patch. This is passed directly without a flag name, at the end of the command.
```
java -jar morphe-desktop-*-all.jar patch -p patches.mpp your_app.apk
```

> [!NOTE]
> Morphe also supports `.apkm` files (split APK bundles). If you pass an `.apkm` file, Morphe will automatically merge the splits before patching.


#### 3. `-o`, `--out`:
Required: No

Default: Same directory as the APK, with `-patched` appended (e.g. `your_app-patched.apk`)

Specify a custom output path for the patched APK.
```
java -jar morphe-desktop-*-all.jar patch -p patches.mpp -o /path/to/output.apk your_app.apk
```


#### 4. `-e`, `--enable`:
Required: No

Default: —

Enable a specific patch by its exact name. Can be used multiple times to enable several patches.
```
java -jar morphe-desktop-*-all.jar patch -p patches.mpp -e "Patch name" -e "Another patch" your_app.apk
```


#### 5. `--ei`:
Required: No

Default: —

Enable a specific patch by its index number. Can be used multiple times. Use `list-patches` to find the index of each patch.
```
java -jar morphe-desktop-*-all.jar patch -p patches.mpp --ei 1 --ei 5 your_app.apk
```


#### 6. `-d`, `--disable`:
Required: No

Default: —

Disable a specific patch by its exact name. Can be used multiple times.
```
java -jar morphe-desktop-*-all.jar patch -p patches.mpp -d "Patch name" your_app.apk
```


#### 7. `--di`:
Required: No

Default: —

Disable a specific patch by its index number. Can be used multiple times. Use `list-patches` to find the index of each patch.
```
java -jar morphe-desktop-*-all.jar patch -p patches.mpp --di 456 your_app.apk
```

> [!TIP]
> You can combine `-e`, `-d`, `--ei`, `--di` and `--exclusive` in the same command.
> ```
> java -jar morphe-desktop-*-all.jar patch -p patches.mpp --exclusive -e "Patch name" --ei 123 your_app.apk
> ```


#### 8. `-O`, `--options`:
Required: No

Default: —

Set option values for patches. Options are key-value pairs passed alongside patch enable flags. To set a value to null, omit the value.
```
java -jar morphe-desktop-*-all.jar patch -p patches.mpp -e "Patch name" -Okey1=value1 -Okey2=value2 your_app.apk
```

To set an option to null:
```
java -jar morphe-desktop-*-all.jar patch -p patches.mpp -e "Patch name" -Okey1 your_app.apk
```

> [!WARNING]
> Option values are typed. Setting a value with the wrong type can cause the patch to fail. Use `list-patches --with-options` to see the expected types.
>
> Common types: `string`, `true`/`false`, `123` (integer), `1.0` (double), `[item1,item2]` (list)


#### 9. `--exclusive`:
Required: No

Default: `false`

Disable all patches except the ones you explicitly enable with `-e` or `--ei`. Useful when you only want a specific set of patches applied.
```
java -jar morphe-desktop-*-all.jar patch -p patches.mpp --exclusive -e "Patch name" your_app.apk
```


#### 10. `-f`, `--force`:
Required: No

Default: `false`

Skip the APK version compatibility check. By default, Morphe will warn you and skip patches that aren't compatible with your APK's version. This flag forces all compatible patches to run regardless.
```
java -jar morphe-desktop-*-all.jar patch -p patches.mpp --force your_app.apk
```

> [!TIP]
> Patches are built for specific app versions. 
> If you're using versions that are newer or older than the recommended ones, Morphe will skip all the incompatible patches (which is almost all of them since the version don't match) by default. **Use `--force` to apply them anyway** — they may still work fine, especially on recent versions.


#### 11. `-i`, `--install`:
Required: No

Default: —

Automatically install the patched APK to a connected ADB device after patching. If no serial is provided, it installs to the first connected device. You can optionally specify a device serial.
```
java -jar morphe-desktop-*-all.jar patch -p patches.mpp -i your_app.apk
java -jar morphe-desktop-*-all.jar patch -p patches.mpp -i SERIAL123 your_app.apk
```

> [!TIP]
> Make sure ADB is working before using this flag:
> ```
> adb shell exit
> ```


#### 12. `--mount`:
Required: No

Default: `false`

Install the patched APK by mounting it on top of the original app. Requires root access and the original app to be installed on the device.
```
java -jar morphe-desktop-*-all.jar patch -p patches.mpp -i --mount your_app.apk
```

> [!NOTE]
> Make sure you have root permissions:
> ```
> adb shell su -c exit
> ```


#### 13. `--keystore`:
Required: No

Default: Auto-generated keystore next to the output APK

Path to a keystore file containing a private key and certificate pair to sign the patched APK with. If not specified, Morphe generates a new keystore automatically.
```
java -jar morphe-desktop-*-all.jar patch -p patches.mpp --keystore /path/to/keystore.jks your_app.apk
```


#### 14. `--keystore-password`:
Required: No

Default: Empty password

Password to open the keystore file.
```
java -jar morphe-desktop-*-all.jar patch -p patches.mpp --keystore keystore.jks --keystore-password "mypassword" your_app.apk
```


#### 15. `--keystore-entry-alias`:
Required: No

Default: `"Morphe Key"`

The alias of the private key entry inside the keystore.
```
java -jar morphe-desktop-*-all.jar patch -p patches.mpp --keystore keystore.jks --keystore-entry-alias "my-alias" your_app.apk
```


#### 16. `--keystore-entry-password`:
Required: No

Default: Empty password

Password for the specific keystore entry.
```
java -jar morphe-desktop-*-all.jar patch -p patches.mpp --keystore keystore.jks --keystore-entry-password "mypassword" your_app.apk
```

> [!IMPORTANT]
> **Using a keystore from the Morphe Manager Android app?** The Manager uses different defaults than the CLI:
> - CLI default alias: `"Morphe Key"`, password: empty
> - Manager default alias: `"Morphe"`, password: `"Morphe"`
>
> To use your Manager keystore with the CLI:
> ```
> java -jar morphe-desktop-*-all.jar patch -p patches.mpp --keystore morphe.keystore --keystore-entry-alias="Morphe" --keystore-entry-password="Morphe" your_app.apk
> ```


#### 17. `--signer`:
Required: No

Default: `"Morphe"`

The name of the signer embedded in the APK signature.
```
java -jar morphe-desktop-*-all.jar patch -p patches.mpp --signer "My Signer" your_app.apk
```


#### 18. `--unsigned`:
Required: No

Default: `false`

Skip signing the patched APK entirely. The output APK will not be signed and cannot be installed directly on a device without signing it separately.
```
java -jar morphe-desktop-*-all.jar patch -p patches.mpp --unsigned your_app.apk
```


#### 19. `-t`, `--temporary-files-path`:
Required: No

Default: Next to the output APK

Path to a directory where Morphe stores temporary files during patching.
```
java -jar morphe-desktop-*-all.jar patch -p patches.mpp -t /tmp/morphe-temp your_app.apk
```


#### 20. `--purge`:
Required: No

Default: `false`

Delete the temporary files directory after patching is complete.
```
java -jar morphe-desktop-*-all.jar patch -p patches.mpp --purge your_app.apk
```


#### 21. `--custom-aapt2-binary`:
Required: No

Default: Bundled AAPT2 (x86/x86-64 only)

Path to a custom AAPT2 binary. Only needed if you're running on a non-x86 architecture (e.g. ARM Linux). The bundled AAPT2 only works on x86/x86-64 systems.
```
java -jar morphe-desktop-*-all.jar patch -p patches.mpp --custom-aapt2-binary /path/to/aapt2 your_app.apk
```


#### 22. `--force-apktool`:
Required: No

Default: `false`

Force the use of apktool for resource compilation instead of ARSCLib. This is automatically implied if `--custom-aapt2-binary` is specified.
```
java -jar morphe-desktop-*-all.jar patch -p patches.mpp --force-apktool your_app.apk
```


#### 23. `--striplibs`:
Required: No

Default: —

Comma-separated list of native library architectures to **keep**. All other architectures will be stripped from the APK, reducing file size.
```
java -jar morphe-desktop-*-all.jar patch -p patches.mpp --striplibs arm64-v8a,armeabi-v7a your_app.apk
```


#### 24. `--continue-on-error`:
Required: No

Default: `false`

By default, patching stops on the first patch failure. This flag lets Morphe continue applying the remaining patches even if one fails.
```
java -jar morphe-desktop-*-all.jar patch -p patches.mpp --continue-on-error your_app.apk
```


#### 25. `--options-file`:
Required: No

Default: —

Path to a JSON file that controls which patches are enabled/disabled and their option values. Generate one using the `options-create` subcommand.
```
java -jar morphe-desktop-*-all.jar patch -p patches.mpp --options-file options.json your_app.apk
```

> [!NOTE]
> If the file you specify doesn't exist yet, Morphe will automatically generate one with default values at that path and use it for the current patch. This means you can skip `options-create` entirely — just pass a path to a non-existent file and Morphe will create it for you.

> [!TIP]
> The options file is great for repeatable patching. Generate it once (either with `options-create` or by letting `--options-file` auto-generate it), tweak it, and reuse it every time you patch.


#### 26. `--options-update`:
Required: No

Default: `false`

Automatically update the options JSON file after patching to reflect the current patches. Without this flag, the file is left unchanged. New patches get added, removed patches get cleaned up.
```
java -jar morphe-desktop-*-all.jar patch -p patches.mpp --options-file options.json --options-update your_app.apk
```


#### 27. `-r`, `--result-file`:
Required: No

Default: —

Path to save a JSON file containing the patching result, including which patches succeeded, which failed, and any error details.
```
java -jar morphe-desktop-*-all.jar patch -p patches.mpp -r result.json your_app.apk
```

---
</details>


<details open>
<summary><h3 id="subcommand-2-list-patches">Subcommand 2: `list-patches`</h3></summary>

Lists all available patches from the supplied MPP files. Useful for finding patch names, indices, compatible packages, and options before patching.
```
java -jar morphe-desktop-*-all.jar list-patches --patches patches.mpp [flag/s]
```

Here is a quick lookup for all the flags under this subcommand:

| Flag                             | Description                                  |
|----------------------------------|----------------------------------------------|
| `--patches`                      | Paths to .mpp files                          |
| `--out`                          | Write patch list to a file instead of stdout |
| `-d`, `--with-descriptions`      | Show patch descriptions                      |
| `-p`, `--with-packages`          | Show compatible packages                     |
| `-v`, `--with-versions`          | Show compatible versions                     |
| `-o`, `--with-options`           | Show patch options                           |
| `-u`, `--with-universal-patches` | Include patches compatible with any app      |
| `-i`, `--index`                  | Show patch index                             |
| `-f`, `--filter-package-name`    | Filter patches by package name               |


#### 1. `--patches`:
Required: Yes

Default: —

One or more paths to .mpp patch files to list patches from.
```
java -jar morphe-desktop-*-all.jar list-patches --patches patches.mpp
```


#### 2. `--out`:
Required: No

Default: —

Write the patch list to a file instead of printing to stdout. Useful in environments where `>` redirection is not available.
```
java -jar morphe-desktop-*-all.jar list-patches --patches patches.mpp --out patches-list.txt
```


#### 3. `-d`, `--with-descriptions`:
Required: No

Default: `true`

Show the description of each patch. Enabled by default — use `--with-descriptions=false` to hide them.
```
java -jar morphe-desktop-*-all.jar list-patches --patches patches.mpp --with-descriptions=false
```


#### 4. `-p`, `--with-packages`:
Required: No

Default: `false`

Show the packages each patch is compatible with.
```
java -jar morphe-desktop-*-all.jar list-patches --patches patches.mpp --with-packages
```


#### 5. `-v`, `--with-versions`:
Required: No

Default: `false`

Show the compatible app versions for each patch. Requires `--with-packages` to be useful.
```
java -jar morphe-desktop-*-all.jar list-patches --patches patches.mpp --with-packages --with-versions
```


#### 6. `-o`, `--with-options`:
Required: No

Default: `false`

Show the configurable options for each patch, including their keys, types, default values, and possible values.
```
java -jar morphe-desktop-*-all.jar list-patches --patches patches.mpp --with-options
```


#### 7. `-u`, `--with-universal-patches`:
Required: No

Default: `true`

Include patches that are compatible with any app (universal patches). Use `--with-universal-patches=false` to only show patches targeting specific packages.
```
java -jar morphe-desktop-*-all.jar list-patches --patches patches.mpp --with-universal-patches=false
```


#### 8. `-i`, `--index`:
Required: No

Default: `true`

Show the index of each patch. The index can be used with `--ei` and `--di` in the `patch` subcommand.
```
java -jar morphe-desktop-*-all.jar list-patches --patches patches.mpp
```


#### 9. `-f`, `--filter-package-name`:
Required: No

Default: —

Only show patches that are compatible with the specified package name.
```
java -jar morphe-desktop-*-all.jar list-patches --patches patches.mpp --filter-package-name com.google.android.youtube
```


---
</details>

<details open>
<summary><h3 id="subcommand-3-list-versions">Subcommand 3: `list-versions`</h3></summary>


Lists the most common compatible app versions for the patches in the supplied MPP files. Useful for knowing which APK version to download before patching.
```
java -jar morphe-desktop-*-all.jar list-versions [flag/s] patches.mpp
```

Here is a quick lookup for all the flags under this subcommand:

| Flag                           | Description                                 |
|--------------------------------|---------------------------------------------|
| *(positional arg)*             | Paths to .mpp files                         |
| `-f`, `--filter-package-names` | Filter by package names                     |
| `-u`, `--count-unused-patches` | Include unused patches in the version count |


#### 1. Positional argument (MPP files):
Required: Yes

Default: —

One or more paths to .mpp patch files.
```
java -jar morphe-desktop-*-all.jar list-versions patches.mpp
```


#### 2. `-f`, `--filter-package-names`:
Required: No

Default: —

Only show versions for the specified package names. Can be used to check versions for a specific app.
```
java -jar morphe-desktop-*-all.jar list-versions -f com.google.android.youtube patches.mpp
```


#### 3. `-u`, `--count-unused-patches`:
Required: No

Default: `false`

Include patches that are not enabled by default when calculating the most common compatible versions. By default, only patches that are enabled are counted.
```
java -jar morphe-desktop-*-all.jar list-versions --count-unused-patches patches.mpp
```

---
</details>


<details open>
<summary><h3 id="subcommand-4-options-create">Subcommand 4: `options-create`</h3></summary>

Creates or updates an options JSON file for controlling which patches are enabled/disabled and their option values. The generated file can be passed to the `patch` subcommand with `--options-file`.
```
java -jar morphe-desktop-*-all.jar options-create [flag/s]
```

Here is a quick lookup for all the flags under this subcommand:

| Flag                          | Description                    |
|-------------------------------|--------------------------------|
| `-p`, `--patches`             | Paths to .mpp files            |
| `-o`, `--out`                 | Path to the output JSON file   |
| `-f`, `--filter-package-name` | Filter patches by package name |


#### 1. `-p`, `--patches`:
Required: Yes

Default: —

One or more paths to .mpp patch files to generate options from.
```
java -jar morphe-desktop-*-all.jar options-create -p patches.mpp -o options.json
```


#### 2. `-o`, `--out`:
Required: Yes

Default: —

Path to the output JSON file. If the file already exists, Morphe will merge the current patches into it — preserving your existing settings, adding new patches, and removing patches that no longer exist.
```
java -jar morphe-desktop-*-all.jar options-create -p patches.mpp -o options.json
```

> [!TIP]
> Run this command again after updating your .mpp file to keep your options file in sync. Existing settings are preserved.


#### 3. `-f`, `--filter-package-name`:
Required: No

Default: —

Only include patches compatible with the specified package name in the generated options file.
```
java -jar morphe-desktop-*-all.jar options-create -p patches.mpp -o options.json -f com.google.android.youtube
```


#### Options JSON Workflow

The options JSON file lets you save your patch preferences and reuse them across multiple patching sessions. Here's the typical workflow:

**Step 1: Generate the options file**

Use `options-create` to generate a JSON file with all available patches and their default settings:
```
java -jar morphe-desktop-*-all.jar options-create -p patches.mpp -o options.json
```

**Step 2: Edit the file**

Open `options.json` in any text editor. You can enable/disable patches and set option values. The file contains a list of patch bundles, each with patch entries that look like:
```json
{
  "patchName": {
    "enabled": true,
    "options": {
      "optionKey": "optionValue"
    }
  }
}
```

Set `"enabled": false` to disable a patch, or change option values as needed.

**Step 3: Patch using the options file**

Pass your customized options file to the `patch` command:
```
java -jar morphe-desktop-*-all.jar patch -p patches.mpp --options-file options.json your_app.apk
```

**Step 4: Keep the file in sync (optional)**

When you update your .mpp file to a newer version, patches may be added or removed. You have two ways to sync:

- Re-run `options-create` — this merges new patches in while preserving your existing settings:
  ```
  java -jar morphe-desktop-*-all.jar options-create -p patches.mpp -o options.json
  ```

- Use `--options-update` during patching — this auto-updates the file after patching:
  ```
  java -jar morphe-desktop-*-all.jar patch -p patches.mpp --options-file options.json --options-update your_app.apk
  ```

> [!NOTE]
> CLI flags (`-e`, `-d`, `--ei`, `--di`, `-O`) always take precedence over the options file. If you enable a patch via CLI that the options file disables, the CLI wins. Morphe will log when this happens.

> [!TIP]
> You can also skip `options-create` entirely. If you pass `--options-file` with a path that doesn't exist yet, Morphe will auto-generate the file with defaults for you.

---
</details>


<details open>
<summary><h3 id="subcommand-5-utility">Subcommand 5: `utility`</h3></summary>

Parent command for utility operations like manually installing or uninstalling apps via ADB. Has two sub-subcommands: `install` and `uninstall`.


#### `utility install`

Manually install an APK file to one or more ADB-connected devices.
```
java -jar morphe-desktop-*-all.jar utility install [flag/s] [device-serial...]
```

| Flag               | Description                                                 |
|--------------------|-------------------------------------------------------------|
| `-a`, `--apk`      | APK file to install                                         |
| `-m`, `--mount`    | Mount over an existing app (requires package name and root) |
| *(positional arg)* | ADB device serial(s)                                        |


##### 1. `-a`, `--apk`:
Required: Yes

Default: —

Path to the APK file you want to install.
```
java -jar morphe-desktop-*-all.jar utility install -a patched_app.apk
```


##### 2. `-m`, `--mount`:
Required: No

Default: —

Mount the APK on top of an existing app instead of a regular install. Pass the package name of the app to mount over. Requires root access.
```
java -jar morphe-desktop-*-all.jar utility install -a patched_app.apk -m com.google.android.youtube
```


##### 3. Positional argument (device serials):
Required: No

Default: First connected device

One or more ADB device serials to install to. If not provided, installs to the first connected device.
```
java -jar morphe-desktop-*-all.jar utility install -a patched_app.apk SERIAL1 SERIAL2
```

---


#### `utility uninstall`

Manually uninstall a patched app from one or more ADB-connected devices.
```
java -jar morphe-desktop-*-all.jar utility uninstall [flag/s] [device-serial...]
```

| Flag                   | Description                          |
|------------------------|--------------------------------------|
| `-p`, `--package-name` | Package name of the app to uninstall |
| `-u`, `--unmount`      | Unmount instead of uninstall         |
| *(positional arg)*     | ADB device serial(s)                 |


##### 1. `-p`, `--package-name`:
Required: Yes

Default: —

The package name of the app to uninstall.
```
java -jar morphe-desktop-*-all.jar utility uninstall -p com.google.android.youtube
```


##### 2. `-u`, `--unmount`:
Required: No

Default: `false`

If the app was installed by mounting (using `--mount`), use this flag to unmount it instead of a regular uninstall.
```
java -jar morphe-desktop-*-all.jar utility uninstall -p com.google.android.youtube --unmount
```


##### 3. Positional argument (device serials):
Required: No

Default: First connected device

One or more ADB device serials to uninstall from. If not provided, uninstalls from the first connected device.
```
java -jar morphe-desktop-*-all.jar utility uninstall -p com.google.android.youtube SERIAL1 SERIAL2
```

---
</details>


### Value Types Reference

When setting patch options with `-O` or in an options JSON file, values are typed. Using the wrong type can cause a patch to fail. Here are the supported types and how to format them:

| Type                  | Example                | Notes                              |
|-----------------------|------------------------|------------------------------------|
| String                | `string`               | Plain text                         |
| Boolean               | `true`, `false`        |                                    |
| Integer               | `123`                  | Whole numbers                      |
| Double                | `1.0`                  | Decimal numbers                    |
| Float                 | `1.0f`                 | Decimal with `f` suffix            |
| Long                  | `1234567890`, `1L`     | Large numbers, optional `L` suffix |
| List                  | `[item1,item2,item3]`  | Comma-separated, no spaces         |
| List (mixed types)    | `[item1,123,true,1.0]` | Items are parsed by their type     |
| Empty list (any type) | `[]`                   |                                    |
| Typed empty list      | `int[]`                | Empty list of a specific type      |
| Nested empty list     | `[int[]]`              |                                    |
| List with null/empty  | `[null,'','"]`         |                                    |

**Escaping:**

Quotes and commas inside strings need to be escaped with `\`:
- `\"` — escaped double quote
- `\'` — escaped single quote
- `\,` — escaped comma (treated as part of the string, not a list separator)

List items are parsed recursively, so escaping works inside lists too:

| What you want       | How to write it       |
|---------------------|-----------------------|
| Integer as a string | `[\'123\']`           |
| Boolean as a string | `[\'true\']`          |
| List as a string    | `[\'[item1,item2]\']` |
| Null as a string    | `[\'null\']`          |

**Example command:**
```
java -jar morphe-desktop-*-all.jar patch -p patches.mpp -e "Patch name" -OstringKey=\'1\' your_app.apk
```

This sets `stringKey` to the string `"1"` instead of the integer `1`.


</details>

<details open>
<summary><h2 id="gui">GUI</h2></summary>

Coming soon.

</details>