resource "aws_acm_certificate" "main" {
  domain_name       = "kdevnest.com"
  validation_method = "DNS"
}

resource "aws_route53_record" "acm_validation" {
  zone_id = aws_route53_zone.main.zone_id

  name = tolist(
    aws_acm_certificate.main.domain_validation_options
  )[0].resource_record_name

  type = tolist(
    aws_acm_certificate.main.domain_validation_options
  )[0].resource_record_type

  records = [
    tolist(
      aws_acm_certificate.main.domain_validation_options
    )[0].resource_record_value
  ]

  ttl = 300
}

resource "aws_acm_certificate_validation" "main" {
  certificate_arn = aws_acm_certificate.main.arn

  validation_record_fqdns = [
    aws_route53_record.acm_validation.fqdn
  ]
}
