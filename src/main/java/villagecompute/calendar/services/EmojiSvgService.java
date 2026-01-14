package villagecompute.calendar.services;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;

/**
 * Service for converting emoji characters to inline SVG graphics. Uses Noto Emoji SVG files (Apache 2.0 licensed) for
 * vector rendering. This ensures emojis render correctly in PDFs without font dependencies.
 *
 * <p>
 * Supports both color (Noto Color Emoji) and monochrome (Noto Emoji) variants.
 */
@ApplicationScoped
public class EmojiSvgService {

    private static final Logger LOG = Logger.getLogger(EmojiSvgService.class);

    // Map from emoji character to SVG content (inner content only, no outer <svg> wrapper)
    // Color SVGs from emoji-svg/ folder
    private final Map<String, String> colorSvgCache = new HashMap<>();
    private final Map<String, String> colorViewBoxCache = new HashMap<>();

    // Monochrome SVGs from emoji-svg-mono/ folder
    private final Map<String, String> monoSvgCache = new HashMap<>();
    private final Map<String, String> monoViewBoxCache = new HashMap<>();

    // Default viewBox for most emojis
    private static final String DEFAULT_VIEWBOX = "0 0 128 128";

    // Map from emoji to Noto Emoji filename (without .svg extension)
    // Noto Emoji files are named: emoji_u{codepoint}.svg or emoji_u{cp1}_u{cp2}.svg for ZWJ
    // sequences. This map includes all emojis from the picker plus holiday-specific ones.
    private static final Map<String, String> EMOJI_TO_FILENAME = new HashMap<>();

    static {
        // === Smileys & People ===
        EMOJI_TO_FILENAME.put("😀", "emoji_u1f600"); // Grinning Face
        EMOJI_TO_FILENAME.put("😃", "emoji_u1f603"); // Grinning Face with Big Eyes
        EMOJI_TO_FILENAME.put("😄", "emoji_u1f604"); // Grinning Face with Smiling Eyes
        EMOJI_TO_FILENAME.put("😁", "emoji_u1f601"); // Beaming Face
        EMOJI_TO_FILENAME.put("😆", "emoji_u1f606"); // Grinning Squinting Face
        EMOJI_TO_FILENAME.put("😅", "emoji_u1f605"); // Grinning Face with Sweat
        EMOJI_TO_FILENAME.put("🤣", "emoji_u1f923"); // ROFL
        EMOJI_TO_FILENAME.put("😂", "emoji_u1f602"); // Face with Tears of Joy
        EMOJI_TO_FILENAME.put("🙂", "emoji_u1f642"); // Slightly Smiling Face
        EMOJI_TO_FILENAME.put("😊", "emoji_u1f60a"); // Smiling Face with Smiling Eyes
        EMOJI_TO_FILENAME.put("😇", "emoji_u1f607"); // Smiling Face with Halo
        EMOJI_TO_FILENAME.put("🥰", "emoji_u1f970"); // Smiling Face with Hearts
        EMOJI_TO_FILENAME.put("😍", "emoji_u1f60d"); // Heart Eyes
        EMOJI_TO_FILENAME.put("🤩", "emoji_u1f929"); // Star-Struck
        EMOJI_TO_FILENAME.put("😘", "emoji_u1f618"); // Face Blowing Kiss
        EMOJI_TO_FILENAME.put("😗", "emoji_u1f617"); // Kissing Face
        EMOJI_TO_FILENAME.put("😚", "emoji_u1f61a"); // Kissing Face Closed Eyes
        EMOJI_TO_FILENAME.put("😋", "emoji_u1f60b"); // Face Savoring Food
        EMOJI_TO_FILENAME.put("😛", "emoji_u1f61b"); // Face with Tongue
        EMOJI_TO_FILENAME.put("🤪", "emoji_u1f92a"); // Zany Face
        EMOJI_TO_FILENAME.put("😎", "emoji_u1f60e"); // Sunglasses
        EMOJI_TO_FILENAME.put("🤓", "emoji_u1f913"); // Nerd Face
        EMOJI_TO_FILENAME.put("🥳", "emoji_u1f973"); // Partying Face
        EMOJI_TO_FILENAME.put("😏", "emoji_u1f60f"); // Smirking Face
        EMOJI_TO_FILENAME.put("😌", "emoji_u1f60c"); // Relieved Face
        EMOJI_TO_FILENAME.put("😴", "emoji_u1f634"); // Sleeping Face
        EMOJI_TO_FILENAME.put("🤒", "emoji_u1f912"); // Face with Thermometer
        EMOJI_TO_FILENAME.put("🤕", "emoji_u1f915"); // Face with Head-Bandage
        EMOJI_TO_FILENAME.put("🤢", "emoji_u1f922"); // Nauseated Face
        EMOJI_TO_FILENAME.put("🤮", "emoji_u1f92e"); // Face Vomiting
        EMOJI_TO_FILENAME.put("🤧", "emoji_u1f927"); // Sneezing Face
        EMOJI_TO_FILENAME.put("🥵", "emoji_u1f975"); // Hot Face
        EMOJI_TO_FILENAME.put("🥶", "emoji_u1f976"); // Cold Face
        EMOJI_TO_FILENAME.put("😱", "emoji_u1f631"); // Face Screaming
        EMOJI_TO_FILENAME.put("😨", "emoji_u1f628"); // Fearful Face
        EMOJI_TO_FILENAME.put("👶", "emoji_u1f476"); // Baby
        EMOJI_TO_FILENAME.put("👧", "emoji_u1f467"); // Girl
        EMOJI_TO_FILENAME.put("🧒", "emoji_u1f9d2"); // Child
        EMOJI_TO_FILENAME.put("👦", "emoji_u1f466"); // Boy
        EMOJI_TO_FILENAME.put("👩", "emoji_u1f469"); // Woman
        EMOJI_TO_FILENAME.put("🧑", "emoji_u1f9d1"); // Person
        EMOJI_TO_FILENAME.put("👨", "emoji_u1f468"); // Man
        EMOJI_TO_FILENAME.put("👵", "emoji_u1f475"); // Old Woman
        EMOJI_TO_FILENAME.put("🧓", "emoji_u1f9d3"); // Older Person
        EMOJI_TO_FILENAME.put("👴", "emoji_u1f474"); // Old Man
        EMOJI_TO_FILENAME.put("👸", "emoji_u1f478"); // Princess
        EMOJI_TO_FILENAME.put("🤴", "emoji_u1f934"); // Prince
        EMOJI_TO_FILENAME.put("🎅", "emoji_u1f385"); // Santa Claus
        EMOJI_TO_FILENAME.put("🤶", "emoji_u1f936"); // Mrs. Claus
        EMOJI_TO_FILENAME.put("🧙", "emoji_u1f9d9"); // Mage
        EMOJI_TO_FILENAME.put("🧚", "emoji_u1f9da"); // Fairy
        EMOJI_TO_FILENAME.put("🧛", "emoji_u1f9db"); // Vampire
        EMOJI_TO_FILENAME.put("🧜", "emoji_u1f9dc"); // Merperson
        EMOJI_TO_FILENAME.put("🧝", "emoji_u1f9dd"); // Elf
        EMOJI_TO_FILENAME.put("🧞", "emoji_u1f9de"); // Genie
        EMOJI_TO_FILENAME.put("👼", "emoji_u1f47c"); // Baby Angel
        EMOJI_TO_FILENAME.put("🤰", "emoji_u1f930"); // Pregnant Woman
        EMOJI_TO_FILENAME.put("👪", "emoji_u1f46a"); // Family
        EMOJI_TO_FILENAME.put("👫", "emoji_u1f46b"); // Man and Woman
        EMOJI_TO_FILENAME.put("👭", "emoji_u1f46d"); // Women Holding Hands
        EMOJI_TO_FILENAME.put("👬", "emoji_u1f46c"); // Men Holding Hands
        EMOJI_TO_FILENAME.put("👷", "emoji_u1f477"); // Construction Worker

        // === Celebrations ===
        EMOJI_TO_FILENAME.put("🎉", "emoji_u1f389"); // Party Popper
        EMOJI_TO_FILENAME.put("🎊", "emoji_u1f38a"); // Confetti Ball
        EMOJI_TO_FILENAME.put("🎂", "emoji_u1f382"); // Birthday Cake
        EMOJI_TO_FILENAME.put("🎁", "emoji_u1f381"); // Wrapped Gift
        EMOJI_TO_FILENAME.put("🎈", "emoji_u1f388"); // Balloon
        EMOJI_TO_FILENAME.put("🎄", "emoji_u1f384"); // Christmas Tree
        EMOJI_TO_FILENAME.put("🎃", "emoji_u1f383"); // Jack-O-Lantern
        EMOJI_TO_FILENAME.put("🎆", "emoji_u1f386"); // Fireworks
        EMOJI_TO_FILENAME.put("🎇", "emoji_u1f387"); // Sparkler
        EMOJI_TO_FILENAME.put("🧨", "emoji_u1f9e8"); // Firecracker
        EMOJI_TO_FILENAME.put("✨", "emoji_u2728"); // Sparkles
        EMOJI_TO_FILENAME.put("🎀", "emoji_u1f380"); // Ribbon
        EMOJI_TO_FILENAME.put("🎗️", "emoji_u1f397"); // Reminder Ribbon
        EMOJI_TO_FILENAME.put("🏆", "emoji_u1f3c6"); // Trophy
        EMOJI_TO_FILENAME.put("🥇", "emoji_u1f947"); // 1st Place Medal
        EMOJI_TO_FILENAME.put("🥈", "emoji_u1f948"); // 2nd Place Medal
        EMOJI_TO_FILENAME.put("🥉", "emoji_u1f949"); // 3rd Place Medal
        EMOJI_TO_FILENAME.put("🎖️", "emoji_u1f396"); // Military Medal
        EMOJI_TO_FILENAME.put("🏅", "emoji_u1f3c5"); // Sports Medal
        EMOJI_TO_FILENAME.put("🎯", "emoji_u1f3af"); // Bullseye
        EMOJI_TO_FILENAME.put("🎪", "emoji_u1f3aa"); // Circus Tent
        EMOJI_TO_FILENAME.put("🎭", "emoji_u1f3ad"); // Performing Arts
        EMOJI_TO_FILENAME.put("🎨", "emoji_u1f3a8"); // Artist Palette
        EMOJI_TO_FILENAME.put("🎬", "emoji_u1f3ac"); // Clapper Board
        EMOJI_TO_FILENAME.put("🎤", "emoji_u1f3a4"); // Microphone
        EMOJI_TO_FILENAME.put("🎧", "emoji_u1f3a7"); // Headphone
        EMOJI_TO_FILENAME.put("🎼", "emoji_u1f3bc"); // Musical Score
        EMOJI_TO_FILENAME.put("🎹", "emoji_u1f3b9"); // Musical Keyboard
        EMOJI_TO_FILENAME.put("🎸", "emoji_u1f3b8"); // Guitar
        EMOJI_TO_FILENAME.put("🎺", "emoji_u1f3ba"); // Trumpet
        EMOJI_TO_FILENAME.put("🎻", "emoji_u1f3bb"); // Violin
        EMOJI_TO_FILENAME.put("🥁", "emoji_u1f941"); // Drum
        EMOJI_TO_FILENAME.put("🎷", "emoji_u1f3b7"); // Saxophone
        EMOJI_TO_FILENAME.put("🪘", "emoji_u1fa98"); // Long Drum
        EMOJI_TO_FILENAME.put("🎵", "emoji_u1f3b5"); // Musical Note
        EMOJI_TO_FILENAME.put("🎶", "emoji_u1f3b6"); // Musical Notes
        EMOJI_TO_FILENAME.put("🎟️", "emoji_u1f39f"); // Admission Tickets
        EMOJI_TO_FILENAME.put("🎫", "emoji_u1f3ab"); // Ticket
        EMOJI_TO_FILENAME.put("🎲", "emoji_u1f3b2"); // Game Die
        EMOJI_TO_FILENAME.put("🎮", "emoji_u1f3ae"); // Video Game
        EMOJI_TO_FILENAME.put("🃏", "emoji_u1f0cf"); // Joker
        EMOJI_TO_FILENAME.put("🀄", "emoji_u1f004"); // Mahjong
        EMOJI_TO_FILENAME.put("🎴", "emoji_u1f3b4"); // Flower Playing Cards
        EMOJI_TO_FILENAME.put("🪅", "emoji_u1fa85"); // Piñata
        EMOJI_TO_FILENAME.put("🪆", "emoji_u1fa86"); // Nesting Dolls
        EMOJI_TO_FILENAME.put("🧸", "emoji_u1f9f8"); // Teddy Bear
        EMOJI_TO_FILENAME.put("🪀", "emoji_u1fa80"); // Yo-Yo
        EMOJI_TO_FILENAME.put("🪁", "emoji_u1fa81"); // Kite
        EMOJI_TO_FILENAME.put("🔮", "emoji_u1f52e"); // Crystal Ball
        EMOJI_TO_FILENAME.put("🪄", "emoji_u1fa84"); // Magic Wand

        // === Hearts & Love ===
        EMOJI_TO_FILENAME.put("❤️", "emoji_u2764"); // Red Heart
        EMOJI_TO_FILENAME.put("🧡", "emoji_u1f9e1"); // Orange Heart
        EMOJI_TO_FILENAME.put("💛", "emoji_u1f49b"); // Yellow Heart
        EMOJI_TO_FILENAME.put("💚", "emoji_u1f49a"); // Green Heart
        EMOJI_TO_FILENAME.put("💙", "emoji_u1f499"); // Blue Heart
        EMOJI_TO_FILENAME.put("💜", "emoji_u1f49c"); // Purple Heart
        EMOJI_TO_FILENAME.put("🖤", "emoji_u1f5a4"); // Black Heart
        EMOJI_TO_FILENAME.put("🤍", "emoji_u1f90d"); // White Heart
        EMOJI_TO_FILENAME.put("🤎", "emoji_u1f90e"); // Brown Heart
        EMOJI_TO_FILENAME.put("💔", "emoji_u1f494"); // Broken Heart
        EMOJI_TO_FILENAME.put("❣️", "emoji_u2763"); // Heart Exclamation
        EMOJI_TO_FILENAME.put("💕", "emoji_u1f495"); // Two Hearts
        EMOJI_TO_FILENAME.put("💞", "emoji_u1f49e"); // Revolving Hearts
        EMOJI_TO_FILENAME.put("💓", "emoji_u1f493"); // Beating Heart
        EMOJI_TO_FILENAME.put("💗", "emoji_u1f497"); // Growing Heart
        EMOJI_TO_FILENAME.put("💖", "emoji_u1f496"); // Sparkling Heart
        EMOJI_TO_FILENAME.put("💘", "emoji_u1f498"); // Heart with Arrow
        EMOJI_TO_FILENAME.put("💝", "emoji_u1f49d"); // Heart with Ribbon
        EMOJI_TO_FILENAME.put("💟", "emoji_u1f49f"); // Heart Decoration
        EMOJI_TO_FILENAME.put("♥️", "emoji_u2665"); // Heart Suit
        EMOJI_TO_FILENAME.put("😻", "emoji_u1f63b"); // Cat Heart Eyes
        EMOJI_TO_FILENAME.put("💑", "emoji_u1f491"); // Couple with Heart
        EMOJI_TO_FILENAME.put("💏", "emoji_u1f48f"); // Kiss
        EMOJI_TO_FILENAME.put("💋", "emoji_u1f48b"); // Kiss Mark
        EMOJI_TO_FILENAME.put("👄", "emoji_u1f444"); // Mouth
        EMOJI_TO_FILENAME.put("🌹", "emoji_u1f339"); // Rose
        EMOJI_TO_FILENAME.put("🥀", "emoji_u1f940"); // Wilted Flower
        EMOJI_TO_FILENAME.put("💐", "emoji_u1f490"); // Bouquet
        EMOJI_TO_FILENAME.put("💒", "emoji_u1f492"); // Wedding
        EMOJI_TO_FILENAME.put("👰", "emoji_u1f470"); // Person with Veil
        EMOJI_TO_FILENAME.put("🤵", "emoji_u1f935"); // Person in Tuxedo
        EMOJI_TO_FILENAME.put("💍", "emoji_u1f48d"); // Ring
        EMOJI_TO_FILENAME.put("🤗", "emoji_u1f917"); // Hugging Face

        // === Nature ===
        EMOJI_TO_FILENAME.put("🌸", "emoji_u1f338"); // Cherry Blossom
        EMOJI_TO_FILENAME.put("💮", "emoji_u1f4ae"); // White Flower
        EMOJI_TO_FILENAME.put("🏵️", "emoji_u1f3f5"); // Rosette
        EMOJI_TO_FILENAME.put("🌺", "emoji_u1f33a"); // Hibiscus
        EMOJI_TO_FILENAME.put("🌻", "emoji_u1f33b"); // Sunflower
        EMOJI_TO_FILENAME.put("🌼", "emoji_u1f33c"); // Blossom
        EMOJI_TO_FILENAME.put("🌷", "emoji_u1f337"); // Tulip
        EMOJI_TO_FILENAME.put("🌱", "emoji_u1f331"); // Seedling
        EMOJI_TO_FILENAME.put("🪴", "emoji_u1fab4"); // Potted Plant
        EMOJI_TO_FILENAME.put("🌲", "emoji_u1f332"); // Evergreen Tree
        EMOJI_TO_FILENAME.put("🌳", "emoji_u1f333"); // Deciduous Tree
        EMOJI_TO_FILENAME.put("🌴", "emoji_u1f334"); // Palm Tree
        EMOJI_TO_FILENAME.put("🌵", "emoji_u1f335"); // Cactus
        EMOJI_TO_FILENAME.put("🌾", "emoji_u1f33e"); // Sheaf of Rice
        EMOJI_TO_FILENAME.put("🌿", "emoji_u1f33f"); // Herb
        EMOJI_TO_FILENAME.put("☘️", "emoji_u2618"); // Shamrock
        EMOJI_TO_FILENAME.put("🍀", "emoji_u1f340"); // Four Leaf Clover
        EMOJI_TO_FILENAME.put("🍁", "emoji_u1f341"); // Maple Leaf
        EMOJI_TO_FILENAME.put("🍂", "emoji_u1f342"); // Fallen Leaf
        EMOJI_TO_FILENAME.put("🍃", "emoji_u1f343"); // Leaf Fluttering
        EMOJI_TO_FILENAME.put("🍄", "emoji_u1f344"); // Mushroom
        EMOJI_TO_FILENAME.put("🐚", "emoji_u1f41a"); // Shell
        EMOJI_TO_FILENAME.put("🪨", "emoji_u1faa8"); // Rock
        EMOJI_TO_FILENAME.put("🌍", "emoji_u1f30d"); // Globe Europe-Africa
        EMOJI_TO_FILENAME.put("🌎", "emoji_u1f30e"); // Globe Americas
        EMOJI_TO_FILENAME.put("🌏", "emoji_u1f30f"); // Globe Asia-Australia
        EMOJI_TO_FILENAME.put("🌙", "emoji_u1f319"); // Crescent Moon
        EMOJI_TO_FILENAME.put("🌚", "emoji_u1f31a"); // New Moon Face
        EMOJI_TO_FILENAME.put("🌝", "emoji_u1f31d"); // Full Moon Face
        EMOJI_TO_FILENAME.put("🌞", "emoji_u1f31e"); // Sun with Face
        EMOJI_TO_FILENAME.put("⭐", "emoji_u2b50"); // Star
        EMOJI_TO_FILENAME.put("🌟", "emoji_u1f31f"); // Glowing Star
        EMOJI_TO_FILENAME.put("💫", "emoji_u1f4ab"); // Dizzy
        EMOJI_TO_FILENAME.put("☀️", "emoji_u2600"); // Sun
        EMOJI_TO_FILENAME.put("🌤️", "emoji_u1f324"); // Sun Behind Small Cloud
        EMOJI_TO_FILENAME.put("⛅", "emoji_u26c5"); // Sun Behind Cloud
        EMOJI_TO_FILENAME.put("🌥️", "emoji_u1f325"); // Sun Behind Large Cloud
        EMOJI_TO_FILENAME.put("🌦️", "emoji_u1f326"); // Sun Behind Rain Cloud
        EMOJI_TO_FILENAME.put("🌈", "emoji_u1f308"); // Rainbow
        EMOJI_TO_FILENAME.put("☁️", "emoji_u2601"); // Cloud
        EMOJI_TO_FILENAME.put("🌧️", "emoji_u1f327"); // Cloud with Rain
        EMOJI_TO_FILENAME.put("⛈️", "emoji_u26c8"); // Cloud with Lightning Rain
        EMOJI_TO_FILENAME.put("🌩️", "emoji_u1f329"); // Cloud with Lightning
        EMOJI_TO_FILENAME.put("❄️", "emoji_u2744"); // Snowflake
        EMOJI_TO_FILENAME.put("🐶", "emoji_u1f436"); // Dog Face
        EMOJI_TO_FILENAME.put("🐱", "emoji_u1f431"); // Cat Face
        EMOJI_TO_FILENAME.put("🐭", "emoji_u1f42d"); // Mouse Face
        EMOJI_TO_FILENAME.put("🐹", "emoji_u1f439"); // Hamster
        EMOJI_TO_FILENAME.put("🐰", "emoji_u1f430"); // Rabbit Face
        EMOJI_TO_FILENAME.put("🦊", "emoji_u1f98a"); // Fox
        EMOJI_TO_FILENAME.put("🐻", "emoji_u1f43b"); // Bear
        EMOJI_TO_FILENAME.put("🐼", "emoji_u1f43c"); // Panda
        EMOJI_TO_FILENAME.put("🐨", "emoji_u1f428"); // Koala
        EMOJI_TO_FILENAME.put("🦁", "emoji_u1f981"); // Lion
        EMOJI_TO_FILENAME.put("🌕", "emoji_u1f315"); // Full Moon
        EMOJI_TO_FILENAME.put("🐿️", "emoji_u1f43f"); // Chipmunk
        EMOJI_TO_FILENAME.put("🦫", "emoji_u1f9ab"); // Beaver

        // === Food & Drink ===
        EMOJI_TO_FILENAME.put("🍕", "emoji_u1f355"); // Pizza
        EMOJI_TO_FILENAME.put("🍔", "emoji_u1f354"); // Hamburger
        EMOJI_TO_FILENAME.put("🍟", "emoji_u1f35f"); // French Fries
        EMOJI_TO_FILENAME.put("🌭", "emoji_u1f32d"); // Hot Dog
        EMOJI_TO_FILENAME.put("🍿", "emoji_u1f37f"); // Popcorn
        EMOJI_TO_FILENAME.put("🧂", "emoji_u1f9c2"); // Salt
        EMOJI_TO_FILENAME.put("🥓", "emoji_u1f953"); // Bacon
        EMOJI_TO_FILENAME.put("🥚", "emoji_u1f95a"); // Egg
        EMOJI_TO_FILENAME.put("🍳", "emoji_u1f373"); // Cooking
        EMOJI_TO_FILENAME.put("🧇", "emoji_u1f9c7"); // Waffle
        EMOJI_TO_FILENAME.put("🥞", "emoji_u1f95e"); // Pancakes
        EMOJI_TO_FILENAME.put("🧈", "emoji_u1f9c8"); // Butter
        EMOJI_TO_FILENAME.put("🥐", "emoji_u1f950"); // Croissant
        EMOJI_TO_FILENAME.put("🍞", "emoji_u1f35e"); // Bread
        EMOJI_TO_FILENAME.put("🥖", "emoji_u1f956"); // Baguette
        EMOJI_TO_FILENAME.put("🥨", "emoji_u1f968"); // Pretzel
        EMOJI_TO_FILENAME.put("🧀", "emoji_u1f9c0"); // Cheese
        EMOJI_TO_FILENAME.put("🥗", "emoji_u1f957"); // Salad
        EMOJI_TO_FILENAME.put("🥙", "emoji_u1f959"); // Stuffed Flatbread
        EMOJI_TO_FILENAME.put("🥪", "emoji_u1f96a"); // Sandwich
        EMOJI_TO_FILENAME.put("🌮", "emoji_u1f32e"); // Taco
        EMOJI_TO_FILENAME.put("🌯", "emoji_u1f32f"); // Burrito
        EMOJI_TO_FILENAME.put("🫔", "emoji_u1fad4"); // Tamale
        EMOJI_TO_FILENAME.put("🥫", "emoji_u1f96b"); // Canned Food
        EMOJI_TO_FILENAME.put("🍝", "emoji_u1f35d"); // Spaghetti
        EMOJI_TO_FILENAME.put("🍜", "emoji_u1f35c"); // Steaming Bowl
        EMOJI_TO_FILENAME.put("🍲", "emoji_u1f372"); // Pot of Food
        EMOJI_TO_FILENAME.put("🍛", "emoji_u1f35b"); // Curry Rice
        EMOJI_TO_FILENAME.put("🍣", "emoji_u1f363"); // Sushi
        EMOJI_TO_FILENAME.put("🍱", "emoji_u1f371"); // Bento Box
        EMOJI_TO_FILENAME.put("🥟", "emoji_u1f95f"); // Dumpling
        EMOJI_TO_FILENAME.put("🦪", "emoji_u1f9aa"); // Oyster
        EMOJI_TO_FILENAME.put("🍤", "emoji_u1f364"); // Fried Shrimp
        EMOJI_TO_FILENAME.put("🍙", "emoji_u1f359"); // Rice Ball
        EMOJI_TO_FILENAME.put("🍚", "emoji_u1f35a"); // Cooked Rice
        EMOJI_TO_FILENAME.put("🍘", "emoji_u1f358"); // Rice Cracker
        EMOJI_TO_FILENAME.put("🍥", "emoji_u1f365"); // Fish Cake
        EMOJI_TO_FILENAME.put("🥠", "emoji_u1f960"); // Fortune Cookie
        EMOJI_TO_FILENAME.put("🥮", "emoji_u1f96e"); // Moon Cake
        EMOJI_TO_FILENAME.put("🍢", "emoji_u1f362"); // Oden
        EMOJI_TO_FILENAME.put("🍡", "emoji_u1f361"); // Dango
        EMOJI_TO_FILENAME.put("🍧", "emoji_u1f367"); // Shaved Ice
        EMOJI_TO_FILENAME.put("🍨", "emoji_u1f368"); // Ice Cream
        EMOJI_TO_FILENAME.put("🍦", "emoji_u1f366"); // Soft Ice Cream
        EMOJI_TO_FILENAME.put("🥧", "emoji_u1f967"); // Pie
        EMOJI_TO_FILENAME.put("🧁", "emoji_u1f9c1"); // Cupcake
        EMOJI_TO_FILENAME.put("🍰", "emoji_u1f370"); // Shortcake
        EMOJI_TO_FILENAME.put("🍮", "emoji_u1f36e"); // Custard
        EMOJI_TO_FILENAME.put("🍭", "emoji_u1f36d"); // Lollipop
        EMOJI_TO_FILENAME.put("🍬", "emoji_u1f36c"); // Candy
        EMOJI_TO_FILENAME.put("🍫", "emoji_u1f36b"); // Chocolate
        EMOJI_TO_FILENAME.put("🍩", "emoji_u1f369"); // Doughnut
        EMOJI_TO_FILENAME.put("🍪", "emoji_u1f36a"); // Cookie
        EMOJI_TO_FILENAME.put("🌰", "emoji_u1f330"); // Chestnut
        EMOJI_TO_FILENAME.put("🥜", "emoji_u1f95c"); // Peanuts
        EMOJI_TO_FILENAME.put("🍯", "emoji_u1f36f"); // Honey Pot
        EMOJI_TO_FILENAME.put("🥛", "emoji_u1f95b"); // Milk
        EMOJI_TO_FILENAME.put("☕", "emoji_u2615"); // Coffee
        EMOJI_TO_FILENAME.put("🍵", "emoji_u1f375"); // Tea
        EMOJI_TO_FILENAME.put("🍎", "emoji_u1f34e"); // Red Apple
        EMOJI_TO_FILENAME.put("🍷", "emoji_u1f377"); // Wine Glass
        EMOJI_TO_FILENAME.put("🍾", "emoji_u1f37e"); // Champagne

        // === Activities ===
        EMOJI_TO_FILENAME.put("⚽", "emoji_u26bd"); // Soccer
        EMOJI_TO_FILENAME.put("🏀", "emoji_u1f3c0"); // Basketball
        EMOJI_TO_FILENAME.put("🏈", "emoji_u1f3c8"); // Football
        EMOJI_TO_FILENAME.put("⚾", "emoji_u26be"); // Baseball
        EMOJI_TO_FILENAME.put("🥎", "emoji_u1f94e"); // Softball
        EMOJI_TO_FILENAME.put("🎾", "emoji_u1f3be"); // Tennis
        EMOJI_TO_FILENAME.put("🏐", "emoji_u1f3d0"); // Volleyball
        EMOJI_TO_FILENAME.put("🏉", "emoji_u1f3c9"); // Rugby
        EMOJI_TO_FILENAME.put("🥏", "emoji_u1f94f"); // Flying Disc
        EMOJI_TO_FILENAME.put("🎱", "emoji_u1f3b1"); // Pool
        EMOJI_TO_FILENAME.put("🏓", "emoji_u1f3d3"); // Ping Pong
        EMOJI_TO_FILENAME.put("🏸", "emoji_u1f3f8"); // Badminton
        EMOJI_TO_FILENAME.put("🏒", "emoji_u1f3d2"); // Ice Hockey
        EMOJI_TO_FILENAME.put("🏑", "emoji_u1f3d1"); // Field Hockey
        EMOJI_TO_FILENAME.put("🥍", "emoji_u1f94d"); // Lacrosse
        EMOJI_TO_FILENAME.put("🏏", "emoji_u1f3cf"); // Cricket
        EMOJI_TO_FILENAME.put("🪃", "emoji_u1fa83"); // Boomerang
        EMOJI_TO_FILENAME.put("🥅", "emoji_u1f945"); // Goal Net
        EMOJI_TO_FILENAME.put("⛳", "emoji_u26f3"); // Golf
        EMOJI_TO_FILENAME.put("🏹", "emoji_u1f3f9"); // Bow and Arrow
        EMOJI_TO_FILENAME.put("🎣", "emoji_u1f3a3"); // Fishing
        EMOJI_TO_FILENAME.put("🤿", "emoji_u1f93f"); // Diving Mask
        EMOJI_TO_FILENAME.put("🥊", "emoji_u1f94a"); // Boxing Glove
        EMOJI_TO_FILENAME.put("🥋", "emoji_u1f94b"); // Martial Arts
        EMOJI_TO_FILENAME.put("🎽", "emoji_u1f3bd"); // Running Shirt
        EMOJI_TO_FILENAME.put("🛹", "emoji_u1f6f9"); // Skateboard
        EMOJI_TO_FILENAME.put("🛼", "emoji_u1f6fc"); // Roller Skate
        EMOJI_TO_FILENAME.put("🛷", "emoji_u1f6f7"); // Sled
        EMOJI_TO_FILENAME.put("⛸️", "emoji_u26f8"); // Ice Skate
        EMOJI_TO_FILENAME.put("🥌", "emoji_u1f94c"); // Curling Stone
        EMOJI_TO_FILENAME.put("🎿", "emoji_u1f3bf"); // Skis
        EMOJI_TO_FILENAME.put("⛷️", "emoji_u26f7"); // Skier
        EMOJI_TO_FILENAME.put("🏂", "emoji_u1f3c2"); // Snowboarder
        EMOJI_TO_FILENAME.put("🪂", "emoji_u1fa82"); // Parachute
        EMOJI_TO_FILENAME.put("🏋️", "emoji_u1f3cb"); // Weight Lifter
        EMOJI_TO_FILENAME.put("🤼", "emoji_u1f93c"); // Wrestling
        EMOJI_TO_FILENAME.put("🤸", "emoji_u1f938"); // Cartwheeling
        EMOJI_TO_FILENAME.put("🤺", "emoji_u1f93a"); // Fencing
        EMOJI_TO_FILENAME.put("⛹️", "emoji_u26f9"); // Bouncing Ball
        EMOJI_TO_FILENAME.put("🤾", "emoji_u1f93e"); // Handball
        EMOJI_TO_FILENAME.put("🏌️", "emoji_u1f3cc"); // Golfing
        EMOJI_TO_FILENAME.put("🏇", "emoji_u1f3c7"); // Horse Racing
        EMOJI_TO_FILENAME.put("🧘", "emoji_u1f9d8"); // Lotus Position
        EMOJI_TO_FILENAME.put("🏄", "emoji_u1f3c4"); // Surfing
        EMOJI_TO_FILENAME.put("🏊", "emoji_u1f3ca"); // Swimming
        EMOJI_TO_FILENAME.put("🤽", "emoji_u1f93d"); // Water Polo
        EMOJI_TO_FILENAME.put("🚣", "emoji_u1f6a3"); // Rowing
        EMOJI_TO_FILENAME.put("🧗", "emoji_u1f9d7"); // Climbing
        EMOJI_TO_FILENAME.put("🚴", "emoji_u1f6b4"); // Biking
        EMOJI_TO_FILENAME.put("🚵", "emoji_u1f6b5"); // Mountain Biking
        EMOJI_TO_FILENAME.put("🏎️", "emoji_u1f3ce"); // Racing Car
        EMOJI_TO_FILENAME.put("🏍️", "emoji_u1f3cd"); // Motorcycle
        EMOJI_TO_FILENAME.put("🤹", "emoji_u1f939"); // Juggling
        EMOJI_TO_FILENAME.put("📸", "emoji_u1f4f8"); // Camera Flash

        // === Travel & Places ===
        EMOJI_TO_FILENAME.put("✈️", "emoji_u2708"); // Airplane
        EMOJI_TO_FILENAME.put("🛫", "emoji_u1f6eb"); // Airplane Departure
        EMOJI_TO_FILENAME.put("🛬", "emoji_u1f6ec"); // Airplane Arrival
        EMOJI_TO_FILENAME.put("🛩️", "emoji_u1f6e9"); // Small Airplane
        EMOJI_TO_FILENAME.put("💺", "emoji_u1f4ba"); // Seat
        EMOJI_TO_FILENAME.put("🚀", "emoji_u1f680"); // Rocket
        EMOJI_TO_FILENAME.put("🛸", "emoji_u1f6f8"); // Flying Saucer
        EMOJI_TO_FILENAME.put("🚁", "emoji_u1f681"); // Helicopter
        EMOJI_TO_FILENAME.put("🛶", "emoji_u1f6f6"); // Canoe
        EMOJI_TO_FILENAME.put("⛵", "emoji_u26f5"); // Sailboat
        EMOJI_TO_FILENAME.put("🚤", "emoji_u1f6a4"); // Speedboat
        EMOJI_TO_FILENAME.put("🛥️", "emoji_u1f6e5"); // Motor Boat
        EMOJI_TO_FILENAME.put("🛳️", "emoji_u1f6f3"); // Passenger Ship
        EMOJI_TO_FILENAME.put("⛴️", "emoji_u26f4"); // Ferry
        EMOJI_TO_FILENAME.put("🚢", "emoji_u1f6a2"); // Ship
        EMOJI_TO_FILENAME.put("🚂", "emoji_u1f682"); // Locomotive
        EMOJI_TO_FILENAME.put("🚃", "emoji_u1f683"); // Railway Car
        EMOJI_TO_FILENAME.put("🚄", "emoji_u1f684"); // High-Speed Train
        EMOJI_TO_FILENAME.put("🚅", "emoji_u1f685"); // Bullet Train
        EMOJI_TO_FILENAME.put("🚆", "emoji_u1f686"); // Train
        EMOJI_TO_FILENAME.put("🚇", "emoji_u1f687"); // Metro
        EMOJI_TO_FILENAME.put("🚈", "emoji_u1f688"); // Light Rail
        EMOJI_TO_FILENAME.put("🚉", "emoji_u1f689"); // Station
        EMOJI_TO_FILENAME.put("🚊", "emoji_u1f68a"); // Tram
        EMOJI_TO_FILENAME.put("🚝", "emoji_u1f69d"); // Monorail
        EMOJI_TO_FILENAME.put("🚞", "emoji_u1f69e"); // Mountain Railway
        EMOJI_TO_FILENAME.put("🚋", "emoji_u1f68b"); // Tram Car
        EMOJI_TO_FILENAME.put("🚌", "emoji_u1f68c"); // Bus
        EMOJI_TO_FILENAME.put("🚍", "emoji_u1f68d"); // Oncoming Bus
        EMOJI_TO_FILENAME.put("🚎", "emoji_u1f68e"); // Trolleybus
        EMOJI_TO_FILENAME.put("🚐", "emoji_u1f690"); // Minibus
        EMOJI_TO_FILENAME.put("🚑", "emoji_u1f691"); // Ambulance
        EMOJI_TO_FILENAME.put("🚒", "emoji_u1f692"); // Fire Engine
        EMOJI_TO_FILENAME.put("🚓", "emoji_u1f693"); // Police Car
        EMOJI_TO_FILENAME.put("🚔", "emoji_u1f694"); // Oncoming Police Car
        EMOJI_TO_FILENAME.put("🚕", "emoji_u1f695"); // Taxi
        EMOJI_TO_FILENAME.put("🚖", "emoji_u1f696"); // Oncoming Taxi
        EMOJI_TO_FILENAME.put("🚗", "emoji_u1f697"); // Car
        EMOJI_TO_FILENAME.put("🚘", "emoji_u1f698"); // Oncoming Car
        EMOJI_TO_FILENAME.put("🚙", "emoji_u1f699"); // SUV
        EMOJI_TO_FILENAME.put("🛻", "emoji_u1f6fb"); // Pickup Truck
        EMOJI_TO_FILENAME.put("🚚", "emoji_u1f69a"); // Delivery Truck
        EMOJI_TO_FILENAME.put("🚛", "emoji_u1f69b"); // Articulated Lorry
        EMOJI_TO_FILENAME.put("🚜", "emoji_u1f69c"); // Tractor
        EMOJI_TO_FILENAME.put("🛵", "emoji_u1f6f5"); // Motor Scooter
        EMOJI_TO_FILENAME.put("🛺", "emoji_u1f6fa"); // Auto Rickshaw
        EMOJI_TO_FILENAME.put("🚲", "emoji_u1f6b2"); // Bicycle
        EMOJI_TO_FILENAME.put("🛴", "emoji_u1f6f4"); // Kick Scooter
        EMOJI_TO_FILENAME.put("🗼", "emoji_u1f5fc"); // Tokyo Tower
        EMOJI_TO_FILENAME.put("🗽", "emoji_u1f5fd"); // Statue of Liberty
        EMOJI_TO_FILENAME.put("🏰", "emoji_u1f3f0"); // Castle
        EMOJI_TO_FILENAME.put("🏯", "emoji_u1f3ef"); // Japanese Castle
        EMOJI_TO_FILENAME.put("🏟️", "emoji_u1f3df"); // Stadium
        EMOJI_TO_FILENAME.put("🎡", "emoji_u1f3a1"); // Ferris Wheel
        EMOJI_TO_FILENAME.put("🎢", "emoji_u1f3a2"); // Roller Coaster
        EMOJI_TO_FILENAME.put("🎠", "emoji_u1f3a0"); // Carousel Horse
        EMOJI_TO_FILENAME.put("⛲", "emoji_u26f2"); // Fountain
        EMOJI_TO_FILENAME.put("⛱️", "emoji_u26f1"); // Beach Umbrella
        EMOJI_TO_FILENAME.put("🏔️", "emoji_u1f3d4"); // Snow-Capped Mountain
        EMOJI_TO_FILENAME.put("⛰️", "emoji_u26f0"); // Mountain
        EMOJI_TO_FILENAME.put("🛒", "emoji_u1f6d2"); // Shopping Cart

        // === Symbols ===
        EMOJI_TO_FILENAME.put("⚡", "emoji_u26a1"); // High Voltage
        EMOJI_TO_FILENAME.put("🔥", "emoji_u1f525"); // Fire
        EMOJI_TO_FILENAME.put("💥", "emoji_u1f4a5"); // Collision
        EMOJI_TO_FILENAME.put("☄️", "emoji_u2604"); // Comet
        EMOJI_TO_FILENAME.put("💧", "emoji_u1f4a7"); // Droplet
        EMOJI_TO_FILENAME.put("🌊", "emoji_u1f30a"); // Wave
        EMOJI_TO_FILENAME.put("💨", "emoji_u1f4a8"); // Dashing Away
        EMOJI_TO_FILENAME.put("🔔", "emoji_u1f514"); // Bell
        EMOJI_TO_FILENAME.put("💡", "emoji_u1f4a1"); // Light Bulb
        EMOJI_TO_FILENAME.put("🔑", "emoji_u1f511"); // Key
        EMOJI_TO_FILENAME.put("🗝️", "emoji_u1f5dd"); // Old Key
        EMOJI_TO_FILENAME.put("🔒", "emoji_u1f512"); // Locked
        EMOJI_TO_FILENAME.put("🔓", "emoji_u1f513"); // Unlocked
        EMOJI_TO_FILENAME.put("📌", "emoji_u1f4cc"); // Pushpin
        EMOJI_TO_FILENAME.put("📍", "emoji_u1f4cd"); // Round Pushpin
        EMOJI_TO_FILENAME.put("✅", "emoji_u2705"); // Check Mark
        EMOJI_TO_FILENAME.put("❌", "emoji_u274c"); // Cross Mark
        EMOJI_TO_FILENAME.put("❓", "emoji_u2753"); // Question Mark
        EMOJI_TO_FILENAME.put("❗", "emoji_u2757"); // Exclamation
        EMOJI_TO_FILENAME.put("💯", "emoji_u1f4af"); // Hundred Points
        EMOJI_TO_FILENAME.put("🔢", "emoji_u1f522"); // Input Numbers
        EMOJI_TO_FILENAME.put("🔤", "emoji_u1f524"); // Input Letters
        EMOJI_TO_FILENAME.put("🅰️", "emoji_u1f170"); // A Button
        EMOJI_TO_FILENAME.put("🅱️", "emoji_u1f171"); // B Button
        EMOJI_TO_FILENAME.put("🆎", "emoji_u1f18e"); // AB Button
        EMOJI_TO_FILENAME.put("🅾️", "emoji_u1f17e"); // O Button
        EMOJI_TO_FILENAME.put("🆘", "emoji_u1f198"); // SOS Button
        EMOJI_TO_FILENAME.put("⛔", "emoji_u26d4"); // No Entry
        EMOJI_TO_FILENAME.put("🚫", "emoji_u1f6ab"); // Prohibited
        EMOJI_TO_FILENAME.put("♠️", "emoji_u2660"); // Spade Suit
        EMOJI_TO_FILENAME.put("♣️", "emoji_u2663"); // Club Suit
        EMOJI_TO_FILENAME.put("♦️", "emoji_u2666"); // Diamond Suit
        EMOJI_TO_FILENAME.put("🔴", "emoji_u1f534"); // Red Circle
        EMOJI_TO_FILENAME.put("🟠", "emoji_u1f7e0"); // Orange Circle
        EMOJI_TO_FILENAME.put("🟡", "emoji_u1f7e1"); // Yellow Circle
        EMOJI_TO_FILENAME.put("🟢", "emoji_u1f7e2"); // Green Circle
        EMOJI_TO_FILENAME.put("🔵", "emoji_u1f535"); // Blue Circle
        EMOJI_TO_FILENAME.put("🟣", "emoji_u1f7e3"); // Purple Circle
        EMOJI_TO_FILENAME.put("⚫", "emoji_u26ab"); // Black Circle
        EMOJI_TO_FILENAME.put("⚪", "emoji_u26aa"); // White Circle
        EMOJI_TO_FILENAME.put("⚔️", "emoji_u2694"); // Crossed Swords
        EMOJI_TO_FILENAME.put("⚖️", "emoji_u2696"); // Balance Scale

        // === Holiday-Specific (flags, religious, etc.) ===
        EMOJI_TO_FILENAME.put("🇺🇸", "emoji_u1f1fa_1f1f8"); // US Flag
        EMOJI_TO_FILENAME.put("🇲🇽", "emoji_u1f1f2_1f1fd"); // Mexico Flag
        EMOJI_TO_FILENAME.put("🏛️", "emoji_u1f3db"); // Classical Building
        EMOJI_TO_FILENAME.put("🦃", "emoji_u1f983"); // Turkey
        EMOJI_TO_FILENAME.put("💀", "emoji_u1f480"); // Skull
        EMOJI_TO_FILENAME.put("🐟", "emoji_u1f41f"); // Fish
        EMOJI_TO_FILENAME.put("✝️", "emoji_u271d"); // Latin Cross
        EMOJI_TO_FILENAME.put("✡️", "emoji_u2721"); // Star of David
        EMOJI_TO_FILENAME.put("🕎", "emoji_u1f54e"); // Menorah
        EMOJI_TO_FILENAME.put("☪️", "emoji_u262a"); // Star and Crescent
        EMOJI_TO_FILENAME.put("🤲", "emoji_u1f932"); // Palms Up Together
        EMOJI_TO_FILENAME.put("🪔", "emoji_u1fa94"); // Diya Lamp
        EMOJI_TO_FILENAME.put("🪈", "emoji_u1fa88"); // Flute
        EMOJI_TO_FILENAME.put("🧧", "emoji_u1f9e7"); // Red Envelope
        EMOJI_TO_FILENAME.put("🏮", "emoji_u1f3ee"); // Red Lantern
        EMOJI_TO_FILENAME.put("🪦", "emoji_u1faa6"); // Headstone
        EMOJI_TO_FILENAME.put("🕊️", "emoji_u1f54a"); // Dove
        EMOJI_TO_FILENAME.put("🙏", "emoji_u1f64f"); // Folded Hands
        EMOJI_TO_FILENAME.put("🏳️‍🌈", "emoji_u1f3f3_200d_1f308"); // Rainbow Flag
        EMOJI_TO_FILENAME.put("🕯️", "emoji_u1f56f"); // Candle
        EMOJI_TO_FILENAME.put("🐣", "emoji_u1f423"); // Hatching Chick
        EMOJI_TO_FILENAME.put("📜", "emoji_u1f4dc"); // Scroll
        EMOJI_TO_FILENAME.put("👑", "emoji_u1f451"); // Crown
        EMOJI_TO_FILENAME.put("💃", "emoji_u1f483"); // Woman Dancing
        EMOJI_TO_FILENAME.put("👔", "emoji_u1f454"); // Necktie
        EMOJI_TO_FILENAME.put("🐉", "emoji_u1f409"); // Dragon
        EMOJI_TO_FILENAME.put("🐑", "emoji_u1f411"); // Sheep
        EMOJI_TO_FILENAME.put("🐘", "emoji_u1f418"); // Elephant
        EMOJI_TO_FILENAME.put("🦅", "emoji_u1f985"); // Eagle
        // Family emoji with ZWJ characters
        EMOJI_TO_FILENAME.put("\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66",
                "emoji_u1f468_200d_1f469_200d_1f467_200d_1f466"); // Family
    }

    @PostConstruct
    void init() {
        LOG.info("Initializing EmojiSvgService - loading emoji SVGs from resources");
        int colorLoaded = 0;
        int monoLoaded = 0;
        int colorFailed = 0;
        int monoFailed = 0;

        for (Map.Entry<String, String> entry : EMOJI_TO_FILENAME.entrySet()) {
            String emoji = entry.getKey();
            String colorFilename = entry.getValue();
            // Mono files use different naming: emoji_uXXXX.svg -> uXXXX.svg
            String monoFilename = colorFilename.replace("emoji_", "");

            // Load color SVG
            String[] colorResult = loadSvgFromResources("emoji-svg/" + colorFilename + ".svg");
            if (colorResult.length > 0) {
                String normalizedEmoji = normalizeEmoji(emoji);
                colorSvgCache.put(normalizedEmoji, colorResult[0]);
                colorViewBoxCache.put(normalizedEmoji, colorResult[1]);
                colorLoaded++;
            } else {
                colorFailed++;
                LOG.debugf("Color SVG not found for emoji %s (file: %s.svg)", emoji, colorFilename);
            }

            // Load monochrome SVG
            String[] monoResult = loadSvgFromResources("emoji-svg-mono/" + monoFilename + ".svg");
            if (monoResult.length > 0) {
                String normalizedEmoji = normalizeEmoji(emoji);
                monoSvgCache.put(normalizedEmoji, monoResult[0]);
                monoViewBoxCache.put(normalizedEmoji, monoResult[1]);
                monoLoaded++;
            } else {
                monoFailed++;
                LOG.debugf("Mono SVG not found for emoji %s (file: %s.svg)", emoji, monoFilename);
            }
        }

        LOG.infof("EmojiSvgService initialized: color=%d/%d, mono=%d/%d", colorLoaded, colorLoaded + colorFailed,
                monoLoaded, monoLoaded + monoFailed);
    }

    /**
     * Load SVG content from resources folder. Returns an array with [innerContent, viewBox] or null if not found.
     *
     * @param resourcePath
     *            Full resource path (e.g., "emoji-svg/emoji_u1f384.svg")
     * @return String array with [innerContent, viewBox] or null
     */
    private String[] loadSvgFromResources(String resourcePath) {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                return new String[0];
            }
            String fullSvg = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8)).lines()
                    .collect(Collectors.joining(System.lineSeparator()));

            // Extract viewBox from the SVG
            String viewBox = extractViewBox(fullSvg);

            // Extract the inner content (we'll wrap it in our own <svg> or <g> when embedding)
            String innerContent = extractSvgInnerContent(fullSvg);

            return new String[]{innerContent, viewBox};
        } catch (Exception e) {
            LOG.warnf("Error loading emoji SVG %s: %s", resourcePath, e.getMessage());
            return new String[0];
        }
    }

    /** Extract viewBox attribute from SVG element. */
    private String extractViewBox(String fullSvg) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("viewBox=\"([^\"]+)\"");
        java.util.regex.Matcher matcher = pattern.matcher(fullSvg);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return DEFAULT_VIEWBOX;
    }

    /**
     * Extract the inner content from an SVG file. Removes the outer <svg> element and returns everything inside.
     */
    private String extractSvgInnerContent(String fullSvg) {
        // Find the opening <svg> tag end
        int svgStart = fullSvg.indexOf("<svg");
        if (svgStart == -1)
            return fullSvg;

        int svgTagEnd = fullSvg.indexOf(">", svgStart);
        if (svgTagEnd == -1)
            return fullSvg;

        // Find the closing </svg> tag
        int svgEnd = fullSvg.lastIndexOf("</svg>");
        if (svgEnd == -1)
            return fullSvg;

        // Return everything between
        return fullSvg.substring(svgTagEnd + 1, svgEnd).trim();
    }

    /**
     * Check if an emoji has an SVG representation available. Returns true if either color or mono SVG is available.
     */
    public boolean hasEmojiSvg(String emoji) {
        String normalized = normalizeEmoji(emoji);
        return colorSvgCache.containsKey(normalized) || monoSvgCache.containsKey(normalized);
    }

    /** Check if an emoji has a specific variant (color or mono) available. */
    public boolean hasEmojiSvg(String emoji, boolean monochrome) {
        String normalized = normalizeEmoji(emoji);
        if (monochrome) {
            return monoSvgCache.containsKey(normalized);
        }
        return colorSvgCache.containsKey(normalized);
    }

    /**
     * Get a standalone SVG for preview purposes (not for embedding in another SVG).
     *
     * @param emoji
     *            The emoji character(s)
     * @param monochrome
     *            If true, use monochrome SVG variant
     * @param colorHex
     *            Optional hex color for colorization (requires monochrome=true)
     * @return Complete standalone SVG string, or null if emoji SVG not available
     */
    public String getStandaloneSvg(String emoji, boolean monochrome, String colorHex) {
        String normalized = normalizeEmoji(emoji);

        String innerContent;
        String viewBox;

        if (monochrome) {
            innerContent = monoSvgCache.get(normalized);
            if (innerContent != null) {
                viewBox = monoViewBoxCache.getOrDefault(normalized, DEFAULT_VIEWBOX);
            } else {
                // Fall back to color SVG
                innerContent = colorSvgCache.get(normalized);
                viewBox = colorViewBoxCache.getOrDefault(normalized, DEFAULT_VIEWBOX);
            }
        } else {
            innerContent = colorSvgCache.get(normalized);
            viewBox = colorViewBoxCache.getOrDefault(normalized, DEFAULT_VIEWBOX);
        }

        if (innerContent == null) {
            return null;
        }

        if (colorHex != null && !colorHex.isEmpty() && monochrome) {
            String color = colorHex.startsWith("#") ? colorHex : "#" + colorHex;
            return String.format(
                    "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"%s\">" + "<g fill=\"%s\">%s</g></svg>",
                    viewBox, color, innerContent);
        } else {
            return String.format("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"%s\">%s</svg>", viewBox,
                    innerContent);
        }
    }

    /**
     * Get the SVG representation of an emoji as an embeddable SVG element.
     *
     * @param emoji
     *            The emoji character(s)
     * @param x
     *            X position
     * @param y
     *            Y position
     * @param size
     *            Size (width and height) in pixels
     * @return SVG element string, or null if emoji SVG not available
     */
    public String getEmojiAsSvg(String emoji, double x, double y, double size) {
        return getEmojiAsSvg(emoji, x, y, size, false);
    }

    /**
     * Get the SVG representation of an emoji as an embeddable SVG element.
     *
     * @param emoji
     *            The emoji character(s)
     * @param x
     *            X position
     * @param y
     *            Y position
     * @param size
     *            Size (width and height) in pixels
     * @param monochrome
     *            If true, use monochrome SVG variant (or fall back to grayscale filter)
     * @return SVG element string, or null if emoji SVG not available
     */
    public String getEmojiAsSvg(String emoji, double x, double y, double size, boolean monochrome) {
        return getEmojiAsSvg(emoji, x, y, size, monochrome, null);
    }

    /**
     * Get the SVG representation of an emoji as an embeddable SVG element with optional colorization.
     *
     * @param emoji
     *            The emoji character(s)
     * @param x
     *            X position
     * @param y
     *            Y position
     * @param size
     *            Size (width and height) in pixels
     * @param monochrome
     *            If true, use monochrome SVG variant (or fall back to grayscale filter)
     * @param colorHex
     *            Optional hex color (e.g., "#DC2626") to colorize the emoji (requires monochrome=true)
     * @return SVG element string, or null if emoji SVG not available
     */
    public String getEmojiAsSvg(String emoji, double x, double y, double size, boolean monochrome, String colorHex) {
        String normalized = normalizeEmoji(emoji);

        // Choose the appropriate cache
        String innerContent;
        String viewBox;
        boolean usingMonoSvg = false;

        if (monochrome) {
            // Try monochrome SVG first
            innerContent = monoSvgCache.get(normalized);
            if (innerContent != null) {
                usingMonoSvg = true;
                viewBox = monoViewBoxCache.getOrDefault(normalized, DEFAULT_VIEWBOX);
            } else {
                // Fall back to color SVG with grayscale filter
                innerContent = colorSvgCache.get(normalized);
                viewBox = colorViewBoxCache.getOrDefault(normalized, DEFAULT_VIEWBOX);
            }
        } else {
            // Use color SVG
            innerContent = colorSvgCache.get(normalized);
            viewBox = colorViewBoxCache.getOrDefault(normalized, DEFAULT_VIEWBOX);
        }

        if (innerContent == null) {
            return null;
        }

        // Make IDs unique per embedded SVG to avoid conflicts and resolution issues
        String uniquePrefix = String.format("e%.0f_%.0f_", x, y);
        innerContent = makeIdsUnique(innerContent, uniquePrefix);

        // Use the correct viewBox from the original SVG
        // We embed as a nested <svg> element with proper positioning and scaling
        // Include xlink namespace for SVGs that use xlink:href attributes
        if (colorHex != null && !colorHex.isEmpty() && monochrome) {
            // Colorized monochrome: wrap content in a group with fill color set
            // This handles SVGs that have no explicit fill (default black) as well as explicit
            // fills
            String color = colorHex.startsWith("#") ? colorHex : "#" + colorHex;
            return String.format(
                    "<svg x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"%.1f\" viewBox=\"%s\""
                            + " xmlns:xlink=\"http://www.w3.org/1999/xlink\"><g" + " fill=\"%s\">%s</g></svg>",
                    x, y, size, size, viewBox, color, innerContent);
        } else if (monochrome && !usingMonoSvg) {
            // Fall back to grayscale filter for color SVG (when mono SVG not available)
            String filterId = uniquePrefix + "grayscale";
            return String.format(
                    "<svg x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"%.1f\" viewBox=\"%s\""
                            + " xmlns:xlink=\"http://www.w3.org/1999/xlink\"><defs><filter"
                            + " id=\"%s\"><feColorMatrix type=\"saturate\""
                            + " values=\"0\"/></filter></defs><g filter=\"url(#%s)\">%s</g></svg>",
                    x, y, size, size, viewBox, filterId, filterId, innerContent);
        } else {
            // Use SVG directly (either color or true mono)
            return String.format(
                    "<svg x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"%.1f\" viewBox=\"%s\""
                            + " xmlns:xlink=\"http://www.w3.org/1999/xlink\">%s</svg>",
                    x, y, size, size, viewBox, innerContent);
        }
    }

    /**
     * Make all IDs in SVG content unique by adding a prefix. This prevents ID collisions when multiple emoji SVGs are
     * embedded in the same document. Updates both id="..." definitions and url(#...) / xlink:href="#..." references.
     */
    private String makeIdsUnique(String svgContent, String prefix) {
        // Find all IDs used in the SVG and replace them with prefixed versions
        // Pattern matches: id="something" and id='something'
        java.util.regex.Pattern idPattern = java.util.regex.Pattern.compile("id=\"([^\"]+)\"");
        java.util.regex.Matcher matcher = idPattern.matcher(svgContent);

        java.util.Set<String> foundIds = new java.util.HashSet<>();
        while (matcher.find()) {
            foundIds.add(matcher.group(1));
        }

        // Replace each ID and its references
        String result = svgContent;
        for (String id : foundIds) {
            String newId = prefix + id;
            // Replace id definitions
            result = result.replace("id=\"" + id + "\"", "id=\"" + newId + "\"");
            // Replace url(#id) references
            result = result.replace("url(#" + id + ")", "url(#" + newId + ")");
            // Replace xlink:href="#id" references
            result = result.replace("xlink:href=\"#" + id + "\"", "xlink:href=\"#" + newId + "\"");
            // Replace href="#id" references (SVG2 style)
            result = result.replace("href=\"#" + id + "\"", "href=\"#" + newId + "\"");
        }

        return result;
    }

    /**
     * Normalize emoji by removing variation selectors for lookup. Variation selector VS16 (U+FE0F) is often appended
     * for emoji presentation.
     */
    private String normalizeEmoji(String emoji) {
        // Remove VS16 (emoji presentation selector)
        return emoji.replace("\uFE0F", "");
    }

    /**
     * Get all available emoji characters that have SVG representations. Returns emojis from both color and mono caches.
     */
    public java.util.Set<String> getAvailableEmojis() {
        java.util.Set<String> all = new java.util.HashSet<>();
        all.addAll(colorSvgCache.keySet());
        all.addAll(monoSvgCache.keySet());
        return all;
    }
}
