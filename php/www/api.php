<?php
$str = 'Hello, World';
$key = '0011223344';
echo base64_encode(hash_hmac('sha256', $str, $key, true));