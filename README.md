# KNotes 📒✨

Aplicación Android de **diarios/cuadernos digitales** escrita en **Kotlin** (100 % original,
arte y contenido generados proceduralmente por la propia app).

## La idea clave que pide la app

> **Los trazos siguen siendo editables y borrables después de guardar, salir del
> cuaderno y volver a entrar.**

Cómo se consigue: cada página almacena **objetos vectoriales** (trazos con lista de
puntos, cajas de texto, stickers-emoji, imágenes y enlaces) serializados en archivos
**JSON** por página (`filesDir/notebooks/<id>/p0.json`). Nada se aplasta en un bitmap,
así que al reabrir el diario puedes:

- **Borrar por zona** — la goma corta los trazos que toca, dejando los pedazos que no tocó.
- **Borrar trazos completos** — goma en modo "trazo".
- **Lazo** — rodea trazos/textos/imagenes y **muévelos**, **escálalos o elimínalos**.
- **Deshacer / rehacer** funcionan sobre cualquier cambio (40 pasos).

## Funciones

- **Cuadernos ilimitados** con portadas procedurales únicas (semilla aleatoria ⇒ cada portada es distinta).
- **Etiquetas** (`todos`, `trabajo`, …) + favoritos ⭐ + buscador.
- **Tipos de página:** rayado (papel crema con línea de margen como la captura), cuadrícula, puntos y en blanco — **con color de papel por página** (crema / blanco / oscuro, serializado en el JSON de cada página).
- **4 medios de trazo:** bolígrafo, rotulador, lápiz suave y **subrayador** translúcido (amarillo como el de la foto, con su paleta completa).
- **Goma** por zona o por trazo, **lazo** para mover/eliminar, **deshacer/rehacer**.
- **Interactividad extra:** texto tecleado, imágenes de la galería, stickers-emoji (Flores/Plantas/Cute/Símbolos/Comida) y **enlaces clicable** (🔗 tócalo para abrir; también copia la URL).
- **Zoom/pan** con dos dedos; indicador de página «1/2»; páginas ilimitadas por cuaderno; duplicar cuadernos, papelera con restaurar o borrar para siempre.
- **Guardado** con un toque (icono 💾) + autoguardado al salir (EditorActivity.onStop); 100 % local, sin internet ni cuentas.
- Mercados y Descubrir con plantillas listas para crear diarios (planners semanales, mensuales, habit tracker… con sticker packs de recuerdos en el editor) funcionando los botones «Usarlo».
- **Se ve bien en móvil y tablet:** rejilla adaptativa (2–4 columnas según ancho), portadas grandes en los diálogos con scroll, y `fitsSystemWindows` para no quedar debajo de la barra del sistema.

### Novedades v1.9.0
- 🛠 **Barra superior del editor**: fila 1 = ← · nombre del diario · deshacer/rehacer · **⋮ Configuración** (botón aparte); fila 2 = **herramientas** (cursor, lazo, pluma, goma, texto, formas, regla, imagen, enlace, stickers) + **guardar**.
- 📄 **Exportar solo el papel**: el PDF ya no lleva fondo crema, márgenes ni «KNotes · pág. 1/1»: cada hoja es el papel a sangre completa con su contenido. Igual en PNG/JPG y en **Word (.docx)**, que ahora lleva una imagen del papel por página.
- ✍️ **Pluma fina con letra elegante** (trazo afilado, curvas suaves) y nuevo pincel **3D Perla** (degradado pastel con volumen, como tus adornos).
- 📐 **Plantillas alineadas**: casillas y líneas ya no se cruzan con el texto; al aplicar una plantilla con contenido la hoja se limpia (sin renglones ni adornos que choquen).
- 👁 Textos del asistente, calendario, tareas y Descubrir se adaptan al tema claro/oscuro (antes podían quedar invisibles).

### Novedades v1.8.0
- ➕ **Asistente «Nuevo diario»** de pantalla completa (como tu video): pestañas *Portada | Página*. Portada: nombre, *Subir imagen*, colores sólidos + paleta y portadas por categorías (Art, Colorful, Vintage, Floral). Página: *Personalizar · Papel · Plantillas* con vista previa → **Crear Nueva Página**.
- ⋮ del inicio = **solo opciones** (Nota rápida, Importar PDF, Importar diario, Ordenar, Acerca). El botón **＋** y la tarjeta «Nuevo Diario» únicamente crean diarios.
- 🧑 **Modo hombre = todo CELESTE**: el acento pasa por el tema (`Overlay.KNotes.Man`), no queda ningún rosa en la interfaz.
- ✍️ **Pluma fina elegante**: estabilizador de entrada (curvas redondas), grosor que responde a presión y velocidad, grosor suavizado y extremos afilados.

### Corrección v1.7.1 — escritura con el pen
- ✍️ El trazo con lápiz digital ya no se dibuja como un polígono «cinta» (que dejaba sombras/huecos al cruzarse): ahora es **tinta continua** de segmentos redondos con grosor según la presión, afilada al empezar y al soltar. Los pinceles translúcidos usan una sola capa, sin oscurecerse donde se solapan.

### Novedades v1.7.0 (la app luce IGUAL que las capturas)

- 🌑 **Tema oscuro por defecto** con los colores muestreados de las capturas (fondo `#181818`, tarjetas `#242424`, rosa `#FFC0CB`). Sigue pudiéndose cambiar en Ajustes.
- 🧱 **Raíl**: cuaderno amarillo en círculo rosa · brújula · tienda · — · ayuda · papelera (sin logo ni engranaje). La brújula abre **Descubrir**.
- 📱 **Barra inferior de 5 iconos sin texto** (Diarios · Calendario · Tareas · Mercado · Ajustes) con círculo rosa en el activo.
- 🏠 **Inicio**: título centrado, regalo/lupa/⋮ a la derecha, filtros «todos / Work» + botón carpeta ★, tarjeta **Nuevo Diario** con oreja rosa, corazón translúcido y ▾ en cada portada, grilla compacta y **dos banners dentro del scroll** (Discover + tutoriales) con ✕.
- 🧭 **Descubrir** con pestañas *inicio · Templates · Stickers · Book Journal · Sample Journals · Sticky* (losetas ilustradas; tocar crea el diario / añade stickers a «Míos»).
- 🗓 **Calendario** (lista · mes · semana · día, clima, objetivos mensuales) y ✅ **Tareas** (caducado · lista · mes · semana · Estadísticas, tira de días). Guardado 100 % local.
- ✍️ **Editor**: flecha, píldora rosa con el nombre del diario (abre el menú de la página), deshacer/rehacer rosa; barra inferior con punta azul + «Acercar/Alejar», bandeja de plumas, goma, 5 grosores y 4 colores + paleta.
- 🖊 **Pinceles**: 8 plumas (Marcador, Lápiz, Bolígrafo, Subrayar, Puntitos, Neón, 3D, **Resaltador** nuevo). **La presión del lápiz afecta a TODOS** (fuerte = grueso, roce = fino), se procesan los puntos históricos del stylus y la presión se suaviza.
- 📄 Páginas nuevas en **blanco** sobre mesa gris cálida; el botón «＋» de página rápida ya se ve también con una sola página.

### Novedades v1.6.0 (build 17 — pulido por feedback)

- 🖊️ **Barra de escritura FIJA abajo (tal cual la captura):** barra oscura redondeada con la **punta del lápiz** (abre cursor/lazo/goma/texto/formas/regla/imagen/enlace/stickers), las **7 plumas con punta de color** (bolígrafo, marcador, lápiz, subrayar, puntitos, neón y **nuevo pincel 3D biselado dorado**), la **regla**, **4 círculos blancos de grosor** y **4 colores rápidos** (violeta, rosa, celeste, amarillo) + botón **paleta 🎨** con rejilla grande. Toque = elegir pluma; **mantener presionado** = sus opciones. Chip **⤢ Acercar/Alejar** sobre la barra.
- ✅ **Ganchito en la ordenación:** las opciones de ordenar (recientes / favoritos / alfabético) muestran **✓** en la elegida.
- 📄 **Exportar recorta el papel:** PNG, JPG, PDF y Compartir exportan **SOLO lo que está dentro de la hoja** — los trazos que se salen del papel se recortan en el borde (nada de mesa ni contenido flotando fuera).
- ⭐ **Plantillas REALES con contenido:** "Cambiar estilo" y "Nueva página" incluyen ahora **10 plantillas que vienen YA DISEÑADAS**: Hábitos de la semana (con casillas por día), Agenda del día, Pendientes con casillas, Presupuesto con tabla, Receta (ingredientes/preparación), Mi semana, Apuntes Cornell, ¿Cómo me siento?, Menú semanal y Reseña de libro. Todo el contenido es **editable** (texto y casillas son objetos normales). La biblioteca temática también descarga diarios con esa primera página lista.
- 📏 **Más formas y con manija de giro:** se añaden **Rombo, Corazón, Nube, Cruz y Hexágono** (11 en total) y, al tocar un objeto con el cursor/lazo, aparece una **manija ⟳ arriba** para **girarlo** con el dedo (texto, formas, imágenes, stickers, enlaces).
- 🔤 **Fuentes y paleta siempre visibles:** los diálogos de texto y de estilo van ahora **dentro de un scroll** (nunca se esconde la sección Tipografía ni la de Fondos) y la **paleta de colores sube a círculos de 44 dp** con borde marcado y anillo rosa en el color activo.
- 🎨 **Cambiar carátula al mantener presionado el diario:** el menú de mantener-presionado trae **"🎨 Cambiar carátula"** primero, y las opciones de ordenar con ✓.

### Novedades v1.6.0 (lote anterior)

- 🐞 **Trazo visible en tiempo real (bug corregido):** el Path del trazo vivo se cacheara en su primer fotograma (un solo punto) y quedaba congelado hasta salir y volver a entrar. Ahora el trazo vivo nunca usa el caché (`strokePath` en `canvas/CanvasPageView.kt`).
- 🧰 **Barra del editor compacta:** herramientas siempre visibles (el menú lateral de acciones pasó a ⋮: nueva página, ver páginas, tipo de página, color del papel). Iconos 46dp con desplazamiento.
- 🧭 **Brújula del raíl = plantillas externas:** galería con 24 plantillas (vista previa detallada por tipo de página + color de papel), botón **Descargar** que crea el diario directamente, e **Importar archivo** (.json) para plantillas o respaldos de terceros.
- 💾 **Icono de la bolsa = respaldo:** crea respaldo de TODOS los diarios **en orden de creación**, eligiendo destino con el selector de Archivos (Descargas o Google Drive) — **JSON restaurable** (metas + páginas + imágenes en base64), TXT y HTML legibles. Además botón **Restaurar** que importa el .json sin pisar tus diarios existentes (los repetidos entran como «(restaurado)»).
- 📤 **Exportar notas** (menú ⋮ del editor): página actual a **PNG** y **JPG**, diario completo a **PDF** (A4, una hoja por página), **TXT** y **Word (.docx mínimo sin librerías)**.
- 🖥 Interfaz del editor con `fitsSystemWindows`; pantalla completa de página sin tapar.

### Novedades v1.3

- 🎨 **Paleta AMPLIA con feedback de elección:** rejilla de 36 colores + círculo de vista previa + animación de pulso al elegir, y **color personalizado** con deslizadores R/G/B (todo en el diálogo del lápiz).
- 🧽 **La goma borra TODO y sin crash:** trazos por zona o completos, y también **stickers, textos, imágenes y enlaces** si los toca. Reescrita para operar por objeto (adiós a los índices corridos que tumbaban la app al borrar mucho) y hacer UNA instantánea de deshacer por arrastre completo.
- ☀️🌙 **Modo claro y oscuro:** se elige manualmente (Automático/Claro/Oscuro) en Ajustes, se recuerda aunque se cierre la app, y «Automático» sigue al sistema; la barra de estado toma el color y el contraste correctos.
- 👩👨 **Modo Mujer / Hombre:** al abrir por primera vez pregunta «¿Cómo usarás KNotes?» y «¿Cómo te llamas?» (ambos quedan guardados). Mujer = acentos rosa y biblioteca romántica; Hombre = acentos AZUL, cuaderno NEGRO con AZUL como ícono del lanzador y biblioteca gym/fútbol/retos/gaming. Se cambia en Ajustes ⇒ «Cambiar sexo» (dos íconos alternativos vía activity-alias).
- 📔 **Título personalizado:** «Todos los diarios de ⟨tu nombre⟩».
- 🔖 **Fecha de creación en cada tarjeta**, debajo del nombre.
- ✂️ **Selección múltiple** (mantener pulsado un diario): barra con contador y acciones **Eliminar (papelera)**, **Mover** (favorito/duplicar/papelera), **Compartir** (genera PDF+PNG+JPG+TXT y abre el selector para compartir) e **Información** (creado / última edición de cada diario seleccionado).
- 🔔 **Actualizaciones (`io/Actualizaciones.kt`)**: marcado «Aqui va tu gist» — pega la URL RAW de un gist JSON `{"version":N,"name":"…","apk":"…"}` en `GIST_URL_HERE` y la app avisa y descarga por DownloadManager. Funciona igual de bien si no se configura.

### Novedades v1.4

- 👩👨 **«Elije tu Sexo» a PANTALLA COMPLETA:** el primer arranque (y ahora también Ajustes ⇒ «Cambiar sexo») muestra dos mitades — rosa 👩 con Mujer y azul 👨 con Hombre, con descripción de lo que incluye cada modo. Un toque y listo.
- ⚡ **Cambio de sexo en tiempo real:** al elegir una mitad desde Ajustes, TODA la app (raíl, barra inferior, ícono del lanzador, plantillas) se re-pinta en el instante.
- ⚙️ **Ajustes imposible de perder:** botón de engranaje nuevo en el raíl izquierdo (siempre visible) y la barra de abajo ahora muestra ETIQUETAS («Ajustes», «Diarios»…).
- 💙 **Interior 100 % adaptado al modo Hombre:** logo del raíl azul con ícono blanco, botón 🎁 azul suave, botón flotante «+» azul intenso, pestañas de Descubrir/Mercado con indicador azul, «Vaciar» de la papelera azul, flechas y botón 💾 del editor azules, y **portadas procedurales sin rosa** (los 3 estilos rosados se intercambian por noche/azul/mostaza en modo hombre).
- 🔵 Azul más intenso (`accent_blue_deep #4A90D9`) para que los íconos y FAB del modo hombre destaquen de verdad.

### Novedades v1.6.0 (la gran tropa de mejoras del papel)

- ✍️ **6 lápices con estilo propio y trazos más finos:** bolígrafo clásico, rotulador, lápiz de grafito (con grano), subrayador, **puntitos** y **brillado neón** — cada uno con su motor de dibujo distinto.
- 🖊 **Presión del stylus (4096 niveles):** escribir y apoyar más fuerte = línea MÁS GRUESA; rozar apenas = trazo fino, como papel real. Funciona con multipresión (palm rejection: la palma NO dibuja; solo la punta del lápiz).
- 📐 **Regla:** actívala (botón 📏), arrástrala, gírala desde su manija — los trazos que empiezan cerca de ella salen perfectamente derechos.
- ⬟ **Formas:** línea, rectángulo, círculo, triángulo, flecha y estrella — eliges la forma y la dibujas arrastrando; sale limpia y editable.
- 📄 **10 tipos de página:** rayado, cuadrícula, puntos, en blanco + **rayado fino, cuadrícula fina, puntos finos, con margen, dos columnas y agenda**.
- 🎨 **Selector de «Fondos» al crear página:** el contenido de Plantillas ahora vive ahí — tocas una plantilla (ej. «Diario de hábitos 🌱») y aplica su estilo + papel + adornos de una vez, con gancho ✓ en lo elegido.
- 🖼 **Portada con FOTO:** al crear un diario puedes cargar una imagen (y luego «Cambiar portada → 🖼 Con mi foto»), o elegir entre **20 diseños predeterminados** nuevos.
- 🧭 **Plantillas arriba de Ajustes**, biblioteca organizada por **temas**: Diario · Hábitos («Diario de hábitos 🌱») · Productividad · Estudio · Viajes · Salud · Cocina.
- ✔ **Gancho ✓ en todo selector:** tipo de página, adornos, papel, tipografías y filtros marcan la opción en uso.
- 🌈 **Paleta de color arreglada:** grilla ordenada de círculos con borde según luminancia + entrada de código RGB **en texto** («#RRGGBB» o «R,G,B» + botón Aplicar).
- 🔤 **Tipografías en el texto:** Estándar, **Times New Roman** (el lápiz bajo el texto en serif 👌), Courier, manuscrita, condensada y oscura.
- ➕ **Botón rápido «nueva página»** junto al número de página: crea la hoja con el MISMO estilo en uso, sin configurar nada.
- 🧭 **Dirección de desplazamiento** elegible (vertical u horizontal) + cambio de página con dos dedos deslizando.
- 📕 **PDF ahora SÍ es la hoja tal cual:** el PDF de la página se exporta SIN el fondo del escritorio — solo la página limpia.
- 📥 **Importar PDF:** cada hoja del PDF se vuelve una página de tu diario (hasta 12 hojas) + **📸 Escanear con la cámara**.
- 🗂 **Menú interior del diario ordenado como pediste:** Nueva Página · Duplicar página · Importar PDF · Escanear · Cambiar estilo · Cambiar papel · Ir a la página · Exportar · Añadir widget · Dirección de desplazamiento · Consejo de uso · Compartir página · Tutoriales · Eliminar · Ver páginas · Limpiar · Acerca del guardado.
- 📱 **Widget del escritorio:** «Añadir widget» explica cómo poner el acceso directo de KNotes en tu pantalla de inicio.
- 🗑 **Botón «Descubrir» retirado** del baja (estaba muerto): queda la biblioteca 🧭 en el raíl y Mercado.
- 🔢 El formato de guardado no cambia (los campos nuevos son opcionales): tus diarios viejos se abren IGUAL que siempre.

### Novedades v1.5.9 (ícono de fábrica blanco y negro, el usuario Define)

- 📔 **El nombre manda su cuadro listening:** si ingresas a la app y AÚN no has definido tu nombre, el cuadro **«¿Cómo te llamas? 📔» sale cada vez que entras** (una vez por sesión), y cuando lo fijas **se apaga para siempre** — ya no molesta. Si borras la app y reinstallás limpio, vuelve el ciclo completo.
- ◼️📓 **Al instalar, el ícono es BLANCO Y NEGRO**: cuaderno blanco con lomo negro y espiral blanca sobre fondo noche. Ni rosa ni celeste — **el usuario Define**: elige 👩 → ROSA; elige 👨 → celeste con negro.
- El anterior ros-dirigido murió: ahora el íónico habla del ESTADO del usuario (sin definir = B&N; definido = su color).

### Novedades v1.5.8 (cambiar sexo siempre refresca el nombre)

- 👩↔️👦 **Al cambiar de sexo, la app SIEMPRE vuelve a preguntar el nombre** — y te lo trae RELLENO con el que ya tenías (solo tocas «Listo» si sigue bien, o lo cambias). Título: «¿Tu nombre sigue siendo este? 📔», y al confirmar sale «Modo 👩/👦 aplicado ✅».
- El de antes (con nombre guardado) NO preguntaba nada al cambiar el sexo: volaba directo al repintado — arreglado.

### Novedades v1.5.7 (primera vez como dios manda)

- 🌸 **El ícono de instalación volvió a ser el ROSA (mujer)** — era así de inicio y se cambia a azul-negro solo si eliges Hombre. (El neutral gris murió, mi error de lecto-escritura 🙏).
- 👩👦 **El questionnaire de primera vez ESTÁ de vuelta siempre en instalación limpia:** desactivo el auto-restaurado del sistema (`allowBackup=false`) — ese mecanismo "revivía" tu sexo y nombre guardados en una instalación otrogada, y por eso JAMÁS se veía «Elije tu Sexo» ni «¿Cómo te llamas?». Ahora en una instalación vacía SIEMPRE pregunta sexo → nombre.
- 📔 Aclaración shiny: si actualizas ENCIMA (instalas una versión de tras de otra) no se re-pregunta — tus diarios quedan tal cual; la pregunta de sexo/nombre aparece SOLO en instalación limpia (borras la app primero).

### Novedades v1.5.6 (anuncio discreto + descarga propia que sí funciona)

- 🛎 **Aviso pequeño arriba, sin molestar:** cuando el gist dice que hay versión nueva aparece una franjita azul arriba del inicio: **«🔔 Nueva versión disponible — vX»** con el botón **Ir** → te tira a la búsqueda de la app (nada de diálogos grandotes al arrancar).
- 📊 **Nueva descarga con progreso:** adiós al DownloadManager del sistema (era el que fallaba con «No se pudo completar la descarga»): ahora la app **descarga lo ELLA con barra de porcentaje y MB**, botón Cancelar, y al terminar **abre el instalador del sistema** directo. Si el enlace responde error, te dice cuál (404/403 → verifica release pública).
- 🏷 El ajuste **«Buscar actualizaciones»** ahora dice **«🔔 Nueva versión disponible — vX»** cuando hay novedad, y picarlo baja la versión al momento.

### Novedades v1.5.5 (ícono neutral de fábrica + buscador actualizado)

- 📓 **Ícono de instalación NEUTRAL:** al instalar la app sale el nuevo cuaderno **blanco con lomo piedra** (ni rosa ni azul). Eliges **Mujer** → rosa; eliges **Hombre** → negro con celeste. Nadie "nacé" con un color que no eligió.
- 🔔 **Buscador de actualizaciones afinado:** cada búsqueda lleva un **sello anti-caché** (ya no te puede servir una revisión viejita del gist), el ajuste dice **"Toca para buscar versión nueva"** (adiós al texto "Gist conectado") y si el JSON del gist está mal escrito (sin «version» numérica) **te lo dice de frente** en vez de creerte que ya estás al día.
- 🆕 La app ahora es **versionCode 11 (v1.5.5)** — el número que tu gist ya declara, así el anuncio usa un **APK real publicado en Releases**.

### Novedades v1.5.4 (cursor con manos largas: agrandar · achicar · girar)

- 🤏 **Nueva barra del cursor:** cuando tienes algo seleccionado en el papel aparecen 4 botones nuevos — **➕ agranda**, **➖ achica** (cada toque escala 15%, pica tantas veces como necesites) y **↻ / ↺ gira** de 15° en 15° hacia cualquier lado.
- 🎯 Funciona con TODO lo que puedas seleccionar: **textos, fotos, stickers, enlaces y trozos del dibujo (trazos)**. Los trazos se giran/escalan punto por punto (de verdad, no un embellecimiento) y el grosor escala con el dibujo.
- 🖼 Las **fotos mantienen su proporción exacta** al agrandarlas o achicarlas; los enlaces ahora también escalan su chip; los stickers llegan hasta 4× su tamaño original.
- 💾 Todo se **guarda con el elemento**: el giro y el tamaño siguen igual cuando cierras y abres el cuaderno (compatible con tus páginas viejas, sin cambios raros).
- 🔔 **Canal de actualizaciones conectado:** la app ahora revisa tu gist (máximo cada 12 h al abrir, o cuando quieras desde **Buscar actualizaciones**) y, cuando el JSON declares una versión mayor, anuncia la nueva con sus notas y la descarga desde GitHub Releases — tus diarios quedan intactos.

### Novedades v1.5.3 (límites del papel + imágenes a prueba de todo)

- ✉️ **El ícono «Crear respaldo (JSON)» pasó a ser UN SOBRE** (el título conserva el 💾 que marcaste).
- 📏 **El papel es EL LÍMITE:** ya no puedes escribir ni dibujar fuera del papel — si tocas fuera de la hoja, no pasa nada; si el trazo llega al borde, se detiene justito ahí (stickers, textos, enlaces y las goma también respetan el límite).
- 🌠 **Importación de imágenes 2× más fuerte:** usa el descodificador moderno del sistema (Android 9+) en adelante: **JPG más caprichoso, PNG, WEBP, HEIC/HEIF (fotos de iPhone/cámara), AVIF, BMP y GIF**. Si una decodificación falla, reintenta por el camino clásico. Además:
  - las fotos de cámara **giran solas** (EXIF) para verse derechas,
  - los PNG con transparencia ya **no se ponen negros** al convertirlos (fondo blanco),
  - y el mensaje de error ahora avisa los formatos soportados.

### Novedades v1.5.2 (arreglos de escritura/imagen/sticker)

- ✍️ **Textos ahora caen BIEN sobre las líneas:** la línea base del texto se ancla EXACTO al renglón del papel (antes la fuente de respaldo de algunos caracteres movía el renglón: uno arriba, otro abajo). Empezarán justo donde tocas y TODOS alineados por igual. Tus textos viejos guardados se ven igual que antes (compatibles).
- 🖼 **La imagen SÍ aparece:** ahora se coloca EN EL CENTRO DE LO QUE ESTÁS VIENDO del papel (antes iba al centro absoluto de la página y parecía que "no cargaba" porque quedaba fuera de la vista). Y si un archivo falla (formato raro) avisa con un mensaje en vez de quedarse callado.
- 🐱 **Modo sticker repetible:** elijes el sticker y luego tocas el papel TODAS las veces que quieras (cada toque pone uno). Para cambiar de sticker o apagar el modo: contacto CORTO en el botón 🖊 lo apaga, y MANTENER PRESIONADO el botón 🖊 vuelve a abrir el selector.

### Novedades v1.5.1 (ajustes de la primera prueba)

- 💾 **Ícono del respaldo → diskette** (el que marcaste con el círculo rojo en el diálogo, como el clásico 💾).
- ℹ️ **Información del diario COMPLETA y legible:** nombre, fecha de creación, última edición, **páginas** y **cuánto pesa** (KB/MB). De paso, el texto blanco-invisible de los diálogos en modo oscuro quedó arreglado a nivel global.
- 🖊 **Lápiz flotante mejor educado:** ARRASTRAR solo lo mueve (no abre nada), **mantener presionado NO abre nada**, y solo un **toque rápido** abre las herramientas (rueda de color, tipo de trazo y grosor).
- 🏷 **Título completo de inicio:** «Todos los diarios de …» ahora vive en su PROPIA fila debajo de los botones 🔍 🎁 ⋮ — ya no se recorta ni lo tapan.
- 🔗 **Enlaces con su nombre:** el chip muestra el NOMBRE que escribiste, centrado y con subrayado; con el CURSOR lo seleccionas para **borrarlo**.
- 🎛 **Rueda de color fuera del papel:** antes flotaba abajo tapando el diario; ahora vive **dentro de la configuración del lápiz** (toque rápido en el lápiz flotante), junto al **"Color elegido"** con pelotita + HEX que cambian AL INSTANTE al elegir color en la rueda o en la paleta.
- 📜 **Barra de texto con scroll:** en textos largos ya ves lo de arriba (campo con scroll interno) y el teclado ya no tapa la pantalla (`adjustResize`).

### Novedades v1.5

- 🚑 **Crash de Descubrir resuelto:** las tarjetas del feed usaban vistas nulas según el layout → ahora Descubrir carga secciones, plantillas y stickers sin cerrarse.
- ⚔️ **Corazón ↔ Espada:** el logo del raíl muestra ♥ dorado en modo Mujer y **ESPADA blanca** en modo Hombre (con los colores del modo).
- ✋🖊 **Palma/dedos NO manchan al escribir con lápiz:** si se detecta un STYLUS/táctil-lápiz, los toques de dedo/palma no dibujan nada (la pUNTA TRASERA del lápiz funciona como goma automática). Sin lápiz, dibujas con uno o varios dedos y el zoom de 2 dedos sigue igual.
- 🖱 **Herramienta CURSOR:** toca texto, trazo, imagen, sticker o enlace y muévelo con libertad (con manija para escalar); si tocas el vacío, habilita el lazo para marcar zona.
- 📝 **Texto que SIGUE las líneas:** la «T» abre una barra de escritura abajo; al confirmar, el texto se ENCAJA en el renglón del papel más cercano y continúa renglón por renglón al escribir salto de línea.
- 🎨 **Rueda de colores en vivo:** sub-barra con disco 12 colores × 4 intensidades (borde intenso → centro pastel), pulso al elegir y «Color elegido» con su HEX actualizándose AL INSTANTE (vale para texto y plumas).
- ✚ **Stickers con «Míos»:** pestaña nueva para AGREGAR los emojis que quieras (se guardan) y **CARGAR IMAGEN** (PNG/JPG) como sticker. Las celdas ya se ven siempre (antes quedaban en blanco por el tema del sistema).
- ✉️ **Respaldo con sobre y DRIVE:** el 💾 pasó a ✉️ y agregamos «☁️ Subir respaldo a Drive (Google)» — genera el JSON restaurable y lo comparte directo a tu Drive.
- 🖊 **Lápiz flotante reubicable:** muévelo donde no estorbe (recuerda la posición) y mantenlo presionado para el acceso rápido a plumón/color/grosor.
- 🏷 **Título y «⋮» robustos:** con nombre largo el título se recorta con «…» y los tres puntos, la lupa y el 🎁 NO desaparecen. Toca el título para SUBIR la rejilla («dame una opción de subir»).
- 📜 **Nombre en Ajustes en tiempo real:** al guardar «Tu nombre» el título cambia al instante (recreación rápida de la actividad).
- 🧽 **Texto blanco invisible corregido** en: campo de nombre, celdas de stickers y los banners «Plantillas y stickers» (colores oscuros fijos sobre papel claro).
- ✅ **Selección múltiple desde 1 diario:** los 4 botones (papelera/mover/compartir/info) operan con UNA tarjeta; al seleccionar, los banners bajos se esconden para ver bien, y la rejilla guarda espacio para el scroll inferior.

## Estructura del código

```
app/src/main/java/com/knotes/app/
├── KNotesApp.kt            # init de persistencia
├── MainActivity.kt         # raíl lateral + barra inferior + launchers de respaldo/importación
├── EditorActivity.kt       # barra de herramientas del editor + guardado + exportaciones
├── data/
│   ├── Model.kt            # Notebook / KPage(+paper) / Stroke / Text/Imagen/Link/Sticker
│   └── Store.kt            # JSON en filesDir + imágenes importadas + adoptNotebook
├── io/
│   └── ExportUtil.kt       # PNG/JPG/PDF/TXT/DOCX con destino SAF
├── canvas/
│   ├── Geo.kt              # distancias, polígono del lazo
│   └── CanvasPageView.kt   # papel interactivo (dibujo, goma, lazo, zoom, render para exportar)
└── ui/
    ├── HomeFragment.kt     # "Todos los diarios"
    ├── FeedFragment.kt     # Descubrir / Mercado
    ├── Catalog.kt          # plantillas + sticker packs (locales)
    ├── NotebookCoverView.kt# portadas procedurales
    ├── ProceduralCardView.kt# miniaturas de tarjetas
    ├── MiscFragments.kt    # Ajustes + Papelera
    ├── BackupUtil.kt       # respaldo JSON/TXT/HTML + restauración (ord. de creación)
    ├── TemplatesLibrary.kt # brújula: 24 plantillas con vista previa + descargar
    └── Panels.kt           # diálogos del editor (lápiz, texto, enlace…)
```

## Cómo compilar

```bash
# Requisitos: JDK 17+, Android SDK 36
gradle assembleDebug          # → app/build/outputs/apk/debug/app-debug.apk
```

El APK entregado (`app-debug.apk`) está firmado con la llave de depuración,
listo para instalar en el móvil (activa «instalar apps de fuentes desconocidas»).

## Nota de licencia/creación

Todo el código y el arte visual (portadas, miniaturas, iconos) son creaciones
originales generadas por Canvas/procedural — no se copió ningún recurso de otras apps.
