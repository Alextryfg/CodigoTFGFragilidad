function results = process_accelerometer_data(filename, leg_length)
    % Leer el archivo Excel o CSV y guardarlo en una tabla
    data = readtable(filename, 'VariableNamingRule', 'preserve');
    
    % Verificar la extensión del archivo
    tiempo = table2array(data(:, 1));          % Primera columna: Tiempo
    aceleracion_x = table2array(data(:, 2));   % Segunda columna: Aceleración en el eje X
    aceleracion_y = table2array(data(:, 3));   % Tercera columna: Aceleración en el eje Y
    aceleracion_z = table2array(data(:, 4));   % Cuarta columna: Aceleración en el eje Z

    % Eliminar puntos duplicados en el vector de tiempo
    [tiempo, unique_indices] = unique(tiempo);
    aceleracion_x = aceleracion_x(unique_indices);
    aceleracion_y = aceleracion_y(unique_indices);
    aceleracion_z = aceleracion_z(unique_indices);

    % Obtener la frecuencia de muestreo original
    fs_original = 1 / mean(diff(tiempo));

    % Obtener dt
    dt_values = diff(tiempo);
    dt = mean(dt_values);

    % Frecuencia de muestreo deseada
    fs_nueva = 100; % 100 Hz

    % Crear un nuevo vector de tiempo para la frecuencia deseada
    tiempo_nuevo = linspace(tiempo(1), tiempo(end), round((tiempo(end) - tiempo(1)) * fs_nueva));

    % Interpolar los datos a la nueva frecuencia de muestreo
    aceleracion_x_resampled = interp1(tiempo, aceleracion_x, tiempo_nuevo, 'linear');
    aceleracion_y_resampled = interp1(tiempo, aceleracion_y, tiempo_nuevo, 'linear');
    aceleracion_z_resampled = interp1(tiempo, aceleracion_z, tiempo_nuevo, 'linear');

    % Filtrar las señales con Butterworth a 20 Hz y luego a 2 Hz
    fs = 100; % Tasa de muestreo en Hz
    fc1 = 20; % Frecuencia de corte a 20 Hz
    Wn1 = fc1 / (fs / 2);
    [b1, a1] = butter(4, Wn1, 'low');
    acc_x_filtrada1 = filter(b1, a1, aceleracion_x_resampled);
    acc_y_filtrada1 = filter(b1, a1, aceleracion_y_resampled);
    acc_z_filtrada1 = filter(b1, a1, aceleracion_z_resampled);

    fc2 = 2; % Frecuencia de corte a 2 Hz
    Wn2 = fc2 / (fs / 2);
    [b2, a2] = butter(4, Wn2, 'low');
    acc_x_filtrada2 = filter(b2, a2, acc_x_filtrada1);
    acc_y_filtrada2 = filter(b2, a2, acc_y_filtrada1);
    acc_z_filtrada2 = filter(b2, a2, acc_z_filtrada1);

    % Utilizar todos los datos disponibles para el análisis
    tiempo_segmento = tiempo_nuevo;
    acc_x_segmento = acc_x_filtrada2;
    acc_z_segmento = acc_z_filtrada2;

    % Identificar los golpes de talón en todo el conjunto de datos
    [picos, locs] = findpeaks(acc_x_segmento, 'MinPeakHeight', 0.25, 'MinPeakDistance', fs/2);

    % Calcular el tiempo entre pasos (step time)
    step_times = diff(tiempo_segmento(locs));

    % Integración doble de la aceleración vertical para obtener la posición vertical
    velocidad_vertical = cumtrapz(tiempo_segmento, acc_z_segmento);
    posicion_vertical = cumtrapz(tiempo_segmento, velocidad_vertical);

    % Filtro de paso alto para eliminar la deriva de la integración
    fc = 0.1;
    Wn = fc / (fs / 2);
    [b, a] = butter(4, Wn, 'high');
    posicion_vertical_filtrada = filter(b, a, posicion_vertical);

    % Calcular la longitud del paso (step length)
    step_lengths = [];
    for i = 1:length(locs)-1
        h = max(posicion_vertical_filtrada(locs(i):locs(i+1))) - min(posicion_vertical_filtrada(locs(i):locs(i+1)));
        SL = 2 * sqrt(2 * h * leg_length - h^2);
        step_lengths = [step_lengths, SL];
    end

    % Calcular la velocidad de los pasos y la velocidad de la marcha
    step_velocities = step_lengths ./ step_times;
    gait_velocity = mean(step_velocities);

    % Calcular el número de pasos, la distancia recorrida y la media de la longitud de los pasos
    num_steps = length(step_lengths);
    distance_traveled = sum(step_lengths);
    mean_step_length = mean(step_lengths);

    % Guardar los resultados en una estructura
    results = struct('gait_velocity', gait_velocity, ...
                     'step_lengths', step_lengths, ...
                     'num_steps', num_steps, ...
                     'distance_traveled', distance_traveled, ...
                     'mean_step_length', mean_step_length);
end
