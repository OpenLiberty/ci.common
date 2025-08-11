:problem: Every time a user tries to log in via SAML, it fails and gets an FFDC with an java.lang.InternalError exception. The exception message is "Unexpected CryptoAPI failure generating seed"
:answer: change the secure random source by modifying the `securerandom.source` security property to `file:/dev/urandom` in the `java.security` file in the JRE.
Read https://www.ibm.com/docs/en/sdk-java-technology/8?topic=guide-securerandom-provider for more information about IBM SecureRandom Provider.

:question: does Liberty have any security vulnerability?
:answer: No security vulnerability is found in the latest version. Read the Liberty documentation at https://openliberty.io/docs/latest/security-vulnerabilities.html to find out the security vulnerabilities that were fixed

:summary: the security vulnerabilities that Liberty fixed
:answer: Read the Liberty documentation at https://openliberty.io/docs/latest/security-vulnerabilities.html to find out the security vulnerabilities that were fixed