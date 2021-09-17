module.exports = {
    // Expose style variables for every components
    css: {
        loaderOptions: {
            sass: {
                additionalData: `
        @import "@/style/variables";
        `,
            },
        },
    },

    // Adding this allows to debug in firefox/chrome
    configureWebpack: {
        devtool: "source-map",
    },

    pluginOptions: {
        i18n: {
            locale: "fr",
            fallbackLocale: "en",
            localeDir: "locales",
            enableInSFC: false,
        },
    },
};