import "./globals.css";

export const metadata = {
  title: "ClickMate — телефон как пульт для Windows",
  description:
    "Управляйте мышью, клавиатурой, фильмами и музыкой на Windows прямо с Android-смартфона в домашней Wi‑Fi сети.",
  icons: { icon: "/icon.png", apple: "/icon.png" },
  openGraph: {
    title: "ClickMate — телефон как пульт для Windows",
    description: "Мышь, клавиатура и медиапульт для компьютера — в одном приложении.",
    type: "website",
    locale: "ru_RU",
  },
};

export default function RootLayout({ children }) {
  return (
    <html lang="ru">
      <body>{children}</body>
    </html>
  );
}
