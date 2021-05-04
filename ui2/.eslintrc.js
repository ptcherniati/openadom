module.exports = {
  root: true,
  env: {
    node: true,
  },
  extends: [
    "plugin:vue/essential",
    "eslint:recommended",
    "@vue/prettier",
    "plugin:@intlify/vue-i18n/recommended",
  ],
  parserOptions: {
    parser: "babel-eslint",
    ecmaVersion: 2020,
    ecmaFeatures: {
      legacyDecorators: true,
    },
  },
  rules: {
    "no-console": process.env.NODE_ENV === "production" ? "warn" : "off",
    "no-debugger": process.env.NODE_ENV === "production" ? "warn" : "off",

    // i18n rules
    "@intlify/vue-i18n/no-duplicate-keys-in-locale": [
      "error",
      {
        ignoreI18nBlock: false,
      },
    ],
    "@intlify/vue-i18n/no-missing-keys": "error",
    "@intlify/vue-i18n/no-missing-keys-in-other-locales": [
      "error",
      {
        ignoreLocales: [],
      },
    ],
    "@intlify/vue-i18n/no-unused-keys": [
      "error",
      {
        src: "./src",
        extensions: [".js", ".vue", "*.ts", "*.json"],
        enableFix: false,
      },
    ],
  },
  settings: {
    "vue-i18n": {
      localeDir: "./src/locales/*.{json,json5,yaml,yml}",

      // Specify the version of `vue-i18n` you are using.
      // If not specified, the message will be parsed twice.
      messageSyntaxVersion: "^8.22.3",
    },
  },
};