package com.example.prototipofragilidad;

import com.mathworks.engine.MatlabEngine;
import java.util.concurrent.ExecutionException;

public class MathOperations {
    public static int sumFromMatlab(int a, int b) {
        int result = 0;
        try {
            // Iniciar el engine de MATLAB
            MatlabEngine eng = MatlabEngine.startMatlab();

            // Crear la expresión MATLAB para sumar dos números
            String expression = String.format("%d + %d", a, b);

            // Evaluar la expresión en MATLAB
            eng.eval("result = " + expression + ";");

            // Obtener el resultado de la variable MATLAB 'result'
            result = eng.getVariable("result");

            // Cerrar el engine de MATLAB
            eng.close();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return result;
    }

    public static void main(String[] args) {
        // Ejemplo de uso
        int sumResult = sumFromMatlab(3, 5);
        System.out.println("La suma de 3 y 5 es: " + sumResult);
    }
}
