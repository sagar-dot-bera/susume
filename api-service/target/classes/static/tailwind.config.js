module.exports = {
    content: [
        "./**/*.html",
    ],
    theme: {
        extend: {
            colors: {
                primary: '#1A1A2E',
                secondary: '#E94560',
                tertiary: '#FFE566',
                neutral: '#F8F8F2',
            },
            fontFamily: {
                headline: ['Space Grotesk', 'sans-serif'],
                body: ['Inter', 'sans-serif'],
            }
        },
    },
    plugins: [require("daisyui")],
    daisyui: {
        themes: ["light", "dark"],
        darkMode: "class",
        base: true,
        styled: true,
        utils: true,
        logs: true,
    },
}
