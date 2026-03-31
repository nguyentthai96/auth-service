# Common Utils

[![version](https://img.shields.io/badge/version-0.0.1-blue.svg)]()[![download](https://img.shields.io/badge/nexus%20repo-link-brightgreen.svg)](http://10.22.7.126:8081/#browse/search/maven)

The `auth-service` module provides a set of utility functions and classes that are commonly used across various
projects.
These utilities help in reducing code duplication and improving code maintainability.

## Table of Contents

- [Features](#features)
    - [String Utilities](#string-utilities)
    - [Date Utilities](#date-utilities)
    - [Collection Utilities](#collection-utilities)
    - [File Utilities](#file-utilities)
    - [Validation Utilities](#validation-utilities)
- [Getting Started](#getting-started)
- [Usage Examples](#usage-examples)
    - [Intergrate common utils for project](#intergrate-common-utils-for-project)
- [Changelog](#changelog)
- [Contribution Guidelines](#contribution-guidelines)
- [License](#license)

## Features

### String Utilities

- **StringUtils**: Provides methods for common string operations such as trimming, splitting, and joining strings.

### Date Utilities

- **DateUtils**: Contains methods for date manipulation and formatting, such as converting dates to different formats
  and calculating date differences.

### Collection Utilities

- **CollectionUtils**: Offers utility methods for working with collections, such as filtering, transforming, and finding
  elements in lists and sets.

### File Utilities

- **FileUtils**: Includes methods for file operations like reading from and writing to files, as well as file path
  manipulations.

### Validation Utilities

- **ValidationUtils**: Provides methods for validating inputs, such as checking for null or empty values, and validating
  email formats.

## Getting Started

To use the `common-utils` module in your project, add the following dependency to your `build.gradle` file:

```groovy
dependencies {
    implementation project(':common-utils')
}
```

## Usage evaluate

### Intergrate common utils for project

Dependencies in build.gradle.kts
```kotlin
build.gradle.kts
dependencies {
    implementation("com.ntt:base-core:0.0.1-SNAPSHOT")
}
```

### Config information
### Performance Considerations
### Error Handling

## Changelog
For a detailed list of changes, see the [CHANGELOG.md](CHANGELOG.md) file.

## Contribution Guidelines
- Fork the repository and create a new branch for your feature or bugfix.
- Write clear and concise commit messages.
- Submit a pull request with a detailed description of your changes.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

This structure ensures that your `common-utils` module follows best practices and is easy to maintain and extend.