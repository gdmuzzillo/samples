package controllers.utils;

public class CuitValidator {

    private static Integer calculaDigitoVerificador(String numeroCUIT){

        String cadena = numeroCUIT.substring( 0, numeroCUIT.length()-1 );
        char[] charArray = cadena.toCharArray();
        Integer multiplicador = 5;
        Integer sumador = 0;

        for(char num : charArray){
            multiplicador = multiplicador == 1 ? 7 : multiplicador;
            sumador = sumador + (Integer.parseInt(String.valueOf(num)) * multiplicador);
            multiplicador = multiplicador -1 ;
        }

        Integer resto = sumador % 11;

        if(resto == 0)
            return 0;
        if(resto == 1)
            return 9;

        return  11-resto;
    };

    public static boolean validaDigitoVerificador(String numeroCUIT) {

        if(numeroCUIT == null)
            return false;

        if(numeroCUIT.length() != 11)
            return false;

        Integer digitoVerificadorIngresado = Integer.parseInt(numeroCUIT.substring(numeroCUIT.length()-1 , numeroCUIT.length() ));
        Integer digitoVerificadorCalculado = calculaDigitoVerificador(numeroCUIT);
        if(digitoVerificadorIngresado != digitoVerificadorCalculado)
            return false;
        return true;
    }

}